package com.example.smarttrip

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.gms.location.*
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import java.util.Locale

data class GpsPoint(
    val latitude: Double,
    val longitude: Double
)

data class PoiPoint(
    val name: String = "",
    val type: String = "",
    val latitude: String = "",
    val longitude: String = "",
    val address: String = "",
    val timestamp: Long = 0
)

class MainActivity : ComponentActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var onPermissionGranted: (() -> Unit)? = null
    private var onPhotoTaken: ((Uri) -> Unit)? = null
    private var currentPhotoUri: Uri? = null

    private val database = FirebaseDatabase.getInstance(
        "https://smarttrip-50485-default-rtdb.europe-west1.firebasedatabase.app"
    )
    private val storage = FirebaseStorage.getInstance()

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                onPermissionGranted?.invoke()
            }
        }

    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                currentPhotoUri?.let { uri ->
                    onPhotoTaken?.invoke(uri)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFFF6F7FB)
                ) {
                    SmartTripApp(
                        onStartTracking = { onLocationUpdate ->
                            startTrackingWithPermission(onLocationUpdate)
                        },
                        onStopTracking = {
                            stopTracking()
                        }
                    )
                }
            }
        }
    }

    fun getBatteryLevel(): Int {
        val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1

        return if (level == -1 || scale == -1) 50
        else (level.toFloat() / scale.toFloat() * 100).toInt()
    }

    fun calculateDistance(points: List<GpsPoint>): Double {
        if (points.size < 2) return 0.0

        var totalDistance = 0.0
        for (i in 0 until points.size - 1) {
            val results = FloatArray(1)
            android.location.Location.distanceBetween(
                points[i].latitude,
                points[i].longitude,
                points[i + 1].latitude,
                points[i + 1].longitude,
                results
            )
            totalDistance += results[0]
        }
        return totalDistance / 1000.0
    }

    fun launchCamera(onResult: (Uri) -> Unit) {
        onPhotoTaken = onResult

        val photoFile = File.createTempFile(
            "photo_${System.currentTimeMillis()}",
            ".jpg",
            cacheDir
        )

        val uri = FileProvider.getUriForFile(
            this,
            "${packageName}.provider",
            photoFile
        )

        currentPhotoUri = uri
        takePictureLauncher.launch(uri)
    }

    fun getAddressFromLocation(lat: Double, lon: Double): String {
        return try {
            val geocoder = Geocoder(this, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lon, 1)

            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                buildString {
                    if (address.thoroughfare != null) append(address.thoroughfare + ", ")
                    if (address.locality != null) append(address.locality + ", ")
                    if (address.countryName != null) append(address.countryName)
                }.ifBlank { "Adresse inconnue" }
            } else {
                "Adresse inconnue"
            }
        } catch (e: Exception) {
            "Adresse inconnue"
        }
    }

    fun saveTrip(tripId: String, tripName: String) {
        val ref = database.getReference("trips/$tripId")
        val trip = mapOf(
            "name" to tripName,
            "createdAt" to System.currentTimeMillis()
        )
        ref.child("info").setValue(trip)
    }

    fun saveGpsPoint(tripId: String, lat: Double, lon: Double) {
        val ref = database.getReference("trips/$tripId/gps_points")
        val point = mapOf(
            "latitude" to lat,
            "longitude" to lon,
            "timestamp" to System.currentTimeMillis()
        )
        ref.push().setValue(point)
    }

    fun savePoi(
        tripId: String,
        name: String,
        type: String,
        lat: String,
        lon: String,
        address: String
    ) {
        val ref = database.getReference("trips/$tripId/pois")
        val poi = PoiPoint(
            name = name,
            type = type,
            latitude = lat,
            longitude = lon,
            address = address,
            timestamp = System.currentTimeMillis()
        )
        ref.push().setValue(poi)
    }

    fun savePhoto(tripId: String, uri: Uri, lat: String, lon: String) {
        val storageRef = storage.reference
            .child("trips/$tripId/photos/${System.currentTimeMillis()}.jpg")

        storageRef.putFile(uri).addOnSuccessListener {
            storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                val ref = database.getReference("trips/$tripId/photos")
                val photo = mapOf(
                    "url" to downloadUri.toString(),
                    "latitude" to lat,
                    "longitude" to lon,
                    "timestamp" to System.currentTimeMillis()
                )
                ref.push().setValue(photo)
            }
        }
    }

    private fun startTrackingWithPermission(onLocationUpdate: (Double, Double) -> Unit) {
        val permission = Manifest.permission.ACCESS_FINE_LOCATION

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            getLastKnownLocation(onLocationUpdate)
            startTracking(onLocationUpdate)
        } else {
            onPermissionGranted = {
                getLastKnownLocation(onLocationUpdate)
                startTracking(onLocationUpdate)
            }
            requestPermissionLauncher.launch(permission)
        }
    }

    @SuppressLint("MissingPermission")
    private fun getLastKnownLocation(onLocationReceived: (Double, Double) -> Unit) {
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                onLocationReceived(location.latitude, location.longitude)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startTracking(onLocationUpdate: (Double, Double) -> Unit) {
        val batteryLevel = getBatteryLevel()
        val interval = when {
            batteryLevel > 50 -> 5000L
            batteryLevel > 20 -> 15000L
            else -> 30000L
        }

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            interval
        ).apply {
            setMinUpdateIntervalMillis(interval)
        }.build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    onLocationUpdate(location.latitude, location.longitude)
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun stopTracking() {
        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }
}

@Composable
fun SmartTripApp(
    onStartTracking: ((Double, Double) -> Unit) -> Unit,
    onStopTracking: () -> Unit
) {
    var currentScreen by remember { mutableStateOf("home") }
    var tripName by remember { mutableStateOf("") }
    var tripId by remember { mutableStateOf("") }
    var showNameDialog by remember { mutableStateOf(false) }

    var selectedHistoryTripId by remember { mutableStateOf("") }
    var selectedHistoryTripName by remember { mutableStateOf("") }

    val gpsPoints = remember { mutableStateListOf<GpsPoint>() }
    val pois = remember { mutableStateListOf<PoiPoint>() }

    val context = LocalContext.current
    val database = FirebaseDatabase.getInstance(
        "https://smarttrip-50485-default-rtdb.europe-west1.firebasedatabase.app"
    )

    if (showNameDialog) {
        TripNameDialog(
            onDismiss = { showNameDialog = false },
            onConfirm = { name ->
                tripName = name
                tripId = "trip_${System.currentTimeMillis()}"

                gpsPoints.clear()
                pois.clear()

                (context as MainActivity).saveTrip(tripId, name)
                showNameDialog = false
                currentScreen = "trip"
            }
        )
    }

    when (currentScreen) {
        "home" -> HomeScreen(
            onCreateTripClick = { showNameDialog = true },
            onHistoryClick = { currentScreen = "history" }
        )

        "trip" -> TripScreen(
            tripName = tripName,
            tripId = tripId,
            gpsPoints = gpsPoints,
            pois = pois,
            onBackClick = { currentScreen = "home" },
            onStartTracking = onStartTracking,
            onStopTracking = onStopTracking,
            onViewMapClick = { currentScreen = "map" }
        )

        "map" -> MapScreen(
            tripName = tripName,
            gpsPoints = gpsPoints,
            pois = pois,
            onBackClick = {
                currentScreen = if (selectedHistoryTripId.isNotBlank()) "trip_detail" else "trip"
            }
        )

        "history" -> HistoryScreen(
            onBackClick = { currentScreen = "home" },
            onTripClick = { trip ->
                selectedHistoryTripId = trip.id
                selectedHistoryTripName = trip.name

                gpsPoints.clear()
                pois.clear()

                database.getReference("trips/${trip.id}/gps_points")
                    .get()
                    .addOnSuccessListener { gpsSnapshot ->
                        for (pointSnap in gpsSnapshot.children) {
                            val lat = pointSnap.child("latitude").getValue(Double::class.java)
                            val lon = pointSnap.child("longitude").getValue(Double::class.java)
                            if (lat != null && lon != null) {
                                gpsPoints.add(GpsPoint(lat, lon))
                            }
                        }

                        database.getReference("trips/${trip.id}/pois")
                            .get()
                            .addOnSuccessListener { poiSnapshot ->
                                for (poiSnap in poiSnapshot.children) {
                                    val poi = poiSnap.getValue(PoiPoint::class.java)
                                    if (poi != null) {
                                        pois.add(poi)
                                    }
                                }

                                tripName = trip.name
                                tripId = trip.id
                                currentScreen = "trip_detail"
                            }
                    }
            }
        )

        "trip_detail" -> TripDetailScreen(
            tripName = selectedHistoryTripName,
            gpsPoints = gpsPoints,
            pois = pois,
            onBackClick = { currentScreen = "history" },
            onViewMapClick = { currentScreen = "map" }
        )
    }
}

@Composable
fun HomeScreen(
    onCreateTripClick: () -> Unit,
    onHistoryClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(90.dp)
                .background(
                    color = Color(0xFF1F3C88),
                    shape = RoundedCornerShape(20.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "✈",
                fontSize = 36.sp,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row {
            Text(
                text = "Smart",
                fontSize = 38.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1F3C88)
            )
            Text(
                text = "Trip",
                fontSize = 38.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFF7043)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Ton compagnon de voyage intelligent",
            fontSize = 15.sp,
            color = Color(0xFF5C7BB5),
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Bienvenue sur SmartTrip",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Démarre ton aventure, suis ton trajet et immortalise chaque instant.",
                    fontSize = 15.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onCreateTripClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1F3C88)
                    )
                ) {
                    Text("Créer mon aventure")
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onHistoryClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Mes voyages", color = Color(0xFF1F3C88))
                }
            }
        }
    }
}

@Composable
fun TripNameDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Nom du voyage", fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    text = "Donne un nom à ton aventure !",
                    fontSize = 14.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Ex : Vacances à Rome...") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) onConfirm(name)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F3C88))
            ) {
                Text("C'est parti !")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}

@Composable
fun TripScreen(
    tripName: String,
    tripId: String,
    gpsPoints: SnapshotStateList<GpsPoint>,
    pois: SnapshotStateList<PoiPoint>,
    onBackClick: () -> Unit,
    onStartTracking: ((Double, Double) -> Unit) -> Unit,
    onStopTracking: () -> Unit,
    onViewMapClick: () -> Unit
) {
    var tripStatus by remember { mutableStateOf("Voyage non démarré") }
    var poiCount by remember { mutableStateOf(0) }
    var photoCount by remember { mutableStateOf(0) }
    var latitude by remember { mutableStateOf("Non disponible") }
    var longitude by remember { mutableStateOf("Non disponible") }
    var isTracking by remember { mutableStateOf(false) }
    var showPoiDialog by remember { mutableStateOf(false) }
    var startTime by remember { mutableStateOf(0L) }
    var elapsedTime by remember { mutableStateOf("00:00:00") }

    val context = LocalContext.current

    val batteryLevel = remember {
        val intent = (context as MainActivity).registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (level == -1 || scale == -1) 50
        else (level.toFloat() / scale.toFloat() * 100).toInt()
    }

    LaunchedEffect(isTracking) {
        if (isTracking) {
            startTime = System.currentTimeMillis()
            while (isTracking) {
                val elapsed = System.currentTimeMillis() - startTime
                val hours = elapsed / 3600000
                val minutes = (elapsed % 3600000) / 60000
                val seconds = (elapsed % 60000) / 1000
                elapsedTime = String.format("%02d:%02d:%02d", hours, minutes, seconds)
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    LaunchedEffect(gpsPoints.size) {
        if (gpsPoints.isNotEmpty()) {
            val lastPoint = gpsPoints.last()
            latitude = lastPoint.latitude.toString()
            longitude = lastPoint.longitude.toString()
        }
        poiCount = pois.size
    }

    if (showPoiDialog) {
        PoiDialog(
            currentLat = latitude,
            currentLon = longitude,
            onDismiss = { showPoiDialog = false },
            onConfirm = { name, type, address ->
                val poi = PoiPoint(
                    name = name,
                    type = type,
                    latitude = latitude,
                    longitude = longitude,
                    address = address,
                    timestamp = System.currentTimeMillis()
                )

                pois.add(poi)
                poiCount = pois.size
                tripStatus = "POI ajouté : $name"

                (context as MainActivity).savePoi(
                    tripId,
                    name,
                    type,
                    latitude,
                    longitude,
                    address
                )
                showPoiDialog = false
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            text = tripName,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1F3C88)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Interface de collecte des données",
            fontSize = 16.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Statut",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = tripStatus,
                    fontSize = 16.sp,
                    color = Color(0xFF333333)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text("Latitude : $latitude", fontSize = 15.sp, color = Color(0xFF444444))
                Spacer(modifier = Modifier.height(6.dp))
                Text("Longitude : $longitude", fontSize = 15.sp, color = Color(0xFF444444))
                Spacer(modifier = Modifier.height(6.dp))
                Text("Points GPS collectés : ${gpsPoints.size}", fontSize = 15.sp, color = Color(0xFF444444))
                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Batterie : $batteryLevel% " + when {
                        batteryLevel > 50 -> "(GPS toutes les 5s)"
                        batteryLevel > 20 -> "(GPS toutes les 15s)"
                        else -> "(GPS toutes les 30s - économie)"
                    },
                    fontSize = 15.sp,
                    color = when {
                        batteryLevel > 50 -> Color(0xFF2E7D32)
                        batteryLevel > 20 -> Color(0xFFFF8F00)
                        else -> Color(0xFFC62828)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    if (!isTracking) {
                        isTracking = true
                        tripStatus = "Suivi GPS démarré"

                        onStartTracking { lat, lon ->
                            latitude = lat.toString()
                            longitude = lon.toString()
                            gpsPoints.add(GpsPoint(lat, lon))
                            (context as MainActivity).saveGpsPoint(tripId, lat, lon)
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
            ) {
                Text("Start Trip")
            }

            Button(
                onClick = {
                    isTracking = false
                    tripStatus = "Suivi GPS arrêté"
                    onStopTracking()
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
            ) {
                Text("Stop Trip")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Actions",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (gpsPoints.isEmpty()) {
                            tripStatus = "Attends le signal GPS avant d'ajouter un POI"
                        } else {
                            showPoiDialog = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
                ) {
                    Text("Ajouter un POI")
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        (context as MainActivity).launchCamera { uri ->
                            photoCount++
                            tripStatus = "Photo ajoutée"
                            (context as MainActivity).savePhoto(tripId, uri, latitude, longitude)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6A1B9A))
                ) {
                    Text("Prendre une photo")
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onViewMapClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00796B))
                ) {
                    Text("Voir la carte")
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Statistiques",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text("Nombre de POI : $poiCount", fontSize = 16.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text("Nombre de photos : $photoCount", fontSize = 16.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text("Points GPS : ${gpsPoints.size}", fontSize = 16.sp)
                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Distance : ${"%.2f".format((context as MainActivity).calculateDistance(gpsPoints))} km",
                    fontSize = 16.sp,
                    color = Color(0xFF1F3C88),
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Durée : $elapsedTime",
                    fontSize = 16.sp,
                    color = Color(0xFF1F3C88),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedButton(
            onClick = onBackClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Retour à l'accueil")
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun PoiDialog(
    currentLat: String,
    currentLon: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, type: String, address: String) -> Unit
) {
    var poiName by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("Restaurant") }
    var address by remember { mutableStateOf("Chargement...") }

    val context = LocalContext.current
    val types = listOf("Restaurant", "Musée", "Hôtel", "Monument", "Plage", "Autre")

    LaunchedEffect(currentLat, currentLon) {
        val lat = currentLat.toDoubleOrNull()
        val lon = currentLon.toDoubleOrNull()

        address = if (lat != null && lon != null) {
            (context as MainActivity).getAddressFromLocation(lat, lon)
        } else {
            "Position non disponible"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Ajouter un point d'intérêt", fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text("Position actuelle :", fontWeight = FontWeight.SemiBold)
                Text(address, fontSize = 13.sp, color = Color(0xFF1F3C88))
                Text("Lat : $currentLat", fontSize = 11.sp, color = Color.LightGray)
                Text("Lon : $currentLon", fontSize = 11.sp, color = Color.LightGray)

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = poiName,
                    onValueChange = { poiName = it },
                    label = { Text("Nom du POI") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text("Type :", fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))

                types.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { type ->
                            FilterChip(
                                selected = selectedType == type,
                                onClick = { selectedType = type },
                                label = { Text(type, fontSize = 12.sp) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (poiName.isNotBlank()) {
                        onConfirm(poiName, selectedType, address)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
            ) {
                Text("Ajouter")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}