package com.example.smarttrip

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import java.io.File

data class GpsPoint(
    val latitude: Double,
    val longitude: Double
)

class MainActivity : ComponentActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var onPermissionGranted: (() -> Unit)? = null
    private var onPhotoTaken: ((Uri) -> Unit)? = null
    private var currentPhotoUri: Uri? = null

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

    private fun startTrackingWithPermission(onLocationUpdate: (Double, Double) -> Unit) {
        val permission = Manifest.permission.ACCESS_FINE_LOCATION
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            startTracking(onLocationUpdate)
        } else {
            onPermissionGranted = { startTracking(onLocationUpdate) }
            requestPermissionLauncher.launch(permission)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startTracking(onLocationUpdate: (Double, Double) -> Unit) {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            5000L
        ).apply {
            setMinUpdateIntervalMillis(3000L)
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
    var showNameDialog by remember { mutableStateOf(false) }

    if (showNameDialog) {
        TripNameDialog(
            onDismiss = { showNameDialog = false },
            onConfirm = { name ->
                tripName = name
                showNameDialog = false
                currentScreen = "trip"
            }
        )
    }

    when (currentScreen) {
        "home" -> HomeScreen(
            onCreateTripClick = { showNameDialog = true }
        )
        "trip" -> TripScreen(
            tripName = tripName,
            onBackClick = { currentScreen = "home" },
            onStartTracking = onStartTracking,
            onStopTracking = onStopTracking
        )
    }
}

@Composable
fun HomeScreen(onCreateTripClick: () -> Unit) {
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
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1F3C88)
                    )
                ) {
                    Text("Créer mon aventure")
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
        title = { Text("Nom du voyage", fontWeight = FontWeight.Bold) },
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
                    label = { Text("Ex: Vacances à Rome...") },
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
    onBackClick: () -> Unit,
    onStartTracking: ((Double, Double) -> Unit) -> Unit,
    onStopTracking: () -> Unit
) {
    var tripStatus by remember { mutableStateOf("Voyage non démarré") }
    var poiCount by remember { mutableStateOf(0) }
    var photoCount by remember { mutableStateOf(0) }
    var latitude by remember { mutableStateOf("Non disponible") }
    var longitude by remember { mutableStateOf("Non disponible") }
    var isTracking by remember { mutableStateOf(false) }
    var showPoiDialog by remember { mutableStateOf(false) }
    val gpsPoints = remember { mutableStateListOf<GpsPoint>() }
    val context = LocalContext.current

    if (showPoiDialog) {
        PoiDialog(
            currentLat = latitude,
            currentLon = longitude,
            onDismiss = { showPoiDialog = false },
            onConfirm = { name, type ->
                poiCount++
                tripStatus = "POI ajouté : $name 📍"
                showPoiDialog = false
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
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
                Text(text = "Statut", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = tripStatus, fontSize = 16.sp, color = Color(0xFF333333))
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "Latitude : $latitude", fontSize = 15.sp, color = Color(0xFF444444))
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = "Longitude : $longitude", fontSize = 15.sp, color = Color(0xFF444444))
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = "Points GPS collectés : ${gpsPoints.size}", fontSize = 15.sp, color = Color(0xFF444444))
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
                        tripStatus = "Suivi GPS démarré 🚀"
                        onStartTracking { lat, lon ->
                            latitude = lat.toString()
                            longitude = lon.toString()
                            gpsPoints.add(GpsPoint(lat, lon))
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
                    tripStatus = "Suivi GPS arrêté 🛑"
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
                Text(text = "Actions", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { showPoiDialog = true },
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
                            tripStatus = "Photo ajoutée 📸"
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6A1B9A))
                ) {
                    Text("Prendre une photo")
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
                Text(text = "Résumé temporaire", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "Nombre de POI : $poiCount", fontSize = 16.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = "Nombre de photos : $photoCount", fontSize = 16.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = "Nombre de points GPS : ${gpsPoints.size}", fontSize = 16.sp)
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
    }
}

@Composable
fun PoiDialog(
    currentLat: String,
    currentLon: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, type: String) -> Unit
) {
    var poiName by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("Restaurant") }
    val types = listOf("Restaurant", "Musée", "Hôtel", "Monument", "Plage", "Autre")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter un point d'intérêt", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Position actuelle :", fontWeight = FontWeight.SemiBold)
                Text("Lat : $currentLat", fontSize = 13.sp, color = Color.Gray)
                Text("Lon : $currentLon", fontSize = 13.sp, color = Color.Gray)

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
                        onConfirm(poiName, selectedType)
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