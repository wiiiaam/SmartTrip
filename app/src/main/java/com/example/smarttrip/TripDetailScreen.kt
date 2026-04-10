package com.example.smarttrip

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.database.FirebaseDatabase

data class PhotoItem(
    val url: String = "",
    val latitude: String = "",
    val longitude: String = "",
    val timestamp: Long = 0
)

@Composable
fun TripDetailScreen(
    tripName: String,
    tripId: String,
    gpsPoints: SnapshotStateList<GpsPoint>,
    pois: SnapshotStateList<PoiPoint>,
    onBackClick: () -> Unit,
    onViewMapClick: () -> Unit
) {
    val database = FirebaseDatabase.getInstance("https://smarttrip-50485-default-rtdb.europe-west1.firebasedatabase.app")
    var photos by remember { mutableStateOf<List<PhotoItem>>(emptyList()) }
    var isLoadingPhotos by remember { mutableStateOf(true) }

    LaunchedEffect(tripId) {
        database.getReference("trips/$tripId/photos")
            .get()
            .addOnSuccessListener { snapshot ->
                val list = mutableListOf<PhotoItem>()

                for (photoSnap in snapshot.children) {
                    val url = photoSnap.child("url").getValue(String::class.java) ?: ""
                    val latitude = photoSnap.child("latitude").getValue(String::class.java) ?: ""
                    val longitude = photoSnap.child("longitude").getValue(String::class.java) ?: ""
                    val timestamp = photoSnap.child("timestamp").getValue(Long::class.java) ?: 0L

                    if (url.isNotBlank()) {
                        list.add(
                            PhotoItem(
                                url = url,
                                latitude = latitude,
                                longitude = longitude,
                                timestamp = timestamp
                            )
                        )
                    }
                }

                photos = list.sortedByDescending { it.timestamp }
                isLoadingPhotos = false
            }
            .addOnFailureListener {
                isLoadingPhotos = false
            }
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
            text = "Détail du voyage",
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
                    text = "Résumé",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("Points GPS : ${gpsPoints.size}", fontSize = 16.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text("Nombre de POI : ${pois.size}", fontSize = 16.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text("Nombre de photos : ${photos.size}", fontSize = 16.sp)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (pois.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Points d’intérêt",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    pois.forEach { poi ->
                        Text(
                            text = "• ${poi.name} (${poi.type})",
                            fontSize = 16.sp
                        )
                        if (poi.address.isNotBlank()) {
                            Text(
                                text = poi.address,
                                fontSize = 13.sp,
                                color = Color.Gray
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Photos du voyage",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                when {
                    isLoadingPhotos -> {
                        CircularProgressIndicator()
                    }

                    photos.isEmpty() -> {
                        Text(
                            text = "Aucune photo enregistrée",
                            fontSize = 15.sp,
                            color = Color.Gray
                        )
                    }

                    else -> {
                        photos.forEach { photo ->
                            AsyncImage(
                                model = photo.url,
                                contentDescription = "Photo du voyage",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp),
                                contentScale = ContentScale.Crop
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Latitude : ${photo.latitude}",
                                fontSize = 13.sp,
                                color = Color.Gray
                            )
                            Text(
                                text = "Longitude : ${photo.longitude}",
                                fontSize = 13.sp,
                                color = Color.Gray
                            )

                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onViewMapClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00796B))
        ) {
            Text("Voir la carte")
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onBackClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Retour")
        }
    }
}