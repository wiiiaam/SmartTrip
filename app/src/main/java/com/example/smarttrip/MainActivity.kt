package com.example.smarttrip

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*

data class GpsPoint(
    val latitude: Double,
    val longitude: Double
)

class MainActivity : ComponentActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var onPermissionGranted: (() -> Unit)? = null

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                onPermissionGranted?.invoke()
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

    private fun startTrackingWithPermission(onLocationUpdate: (Double, Double) -> Unit) {
        val permission = Manifest.permission.ACCESS_FINE_LOCATION

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            startTracking(onLocationUpdate)
        } else {
            onPermissionGranted = {
                startTracking(onLocationUpdate)
            }
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

    when (currentScreen) {
        "home" -> HomeScreen(
            onCreateTripClick = { currentScreen = "trip" }
        )
        "trip" -> TripScreen(
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
        Text(
            text = "SmartTrip",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1F3C88)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Ton compagnon de voyage intelligent",
            fontSize = 18.sp,
            color = Color.DarkGray
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
fun TripScreen(
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
    val gpsPoints = remember { mutableStateListOf<GpsPoint>() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Text(
            text = "Mon voyage",
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

                Text(
                    text = "Latitude : $latitude",
                    fontSize = 15.sp,
                    color = Color(0xFF444444)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Longitude : $longitude",
                    fontSize = 15.sp,
                    color = Color(0xFF444444)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Points GPS collectés : ${gpsPoints.size}",
                    fontSize = 15.sp,
                    color = Color(0xFF444444)
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
                Text(
                    text = "Actions",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        poiCount++
                        tripStatus = "POI ajouté 📍"
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
                        photoCount++
                        tripStatus = "Photo ajoutée 📸"
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
                Text(
                    text = "Résumé temporaire",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp
                )

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
            Text("Retour à l’accueil")
        }
    }
}