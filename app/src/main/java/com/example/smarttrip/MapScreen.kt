package com.example.smarttrip

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState

@Composable
fun MapScreen(
    tripName: String,
    gpsPoints: List<GpsPoint>,
    pois: List<PoiPoint>,
    onBackClick: () -> Unit
) {
    val defaultLocation = LatLng(48.8566, 2.3522)
    val cameraPositionState = rememberCameraPositionState()

    LaunchedEffect(gpsPoints, pois) {
        if (gpsPoints.isNotEmpty()) {
            if (gpsPoints.size == 1) {
                val onlyPoint = LatLng(gpsPoints.first().latitude, gpsPoints.first().longitude)
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(onlyPoint, 15f)
                )
            } else {
                val boundsBuilder = LatLngBounds.Builder()
                gpsPoints.forEach { point ->
                    boundsBuilder.include(LatLng(point.latitude, point.longitude))
                }
                pois.forEach { poi ->
                    val lat = poi.latitude.toDoubleOrNull()
                    val lon = poi.longitude.toDoubleOrNull()
                    if (lat != null && lon != null) {
                        boundsBuilder.include(LatLng(lat, lon))
                    }
                }

                val bounds = boundsBuilder.build()
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngBounds(bounds, 120)
                )
            }
        } else if (pois.isNotEmpty()) {
            val firstPoi = pois.first()
            val lat = firstPoi.latitude.toDoubleOrNull()
            val lon = firstPoi.longitude.toDoubleOrNull()

            if (lat != null && lon != null) {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(LatLng(lat, lon), 15f)
                )
            } else {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(defaultLocation, 12f)
                )
            }
        } else {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(defaultLocation, 12f)
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = tripName,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1F3C88)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${gpsPoints.size} points GPS · ${pois.size} POIs",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }

                OutlinedButton(
                    onClick = onBackClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF1F3C88)
                    )
                ) {
                    Text("Retour")
                }
            }
        }

        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState
        ) {
            // Ligne du trajet
            if (gpsPoints.size >= 2) {
                Polyline(
                    points = gpsPoints.map { LatLng(it.latitude, it.longitude) },
                    color = Color(0xFF1F3C88),
                    width = 10f
                )
            }

            // Point de départ
            if (gpsPoints.isNotEmpty()) {
                Marker(
                    state = MarkerState(
                        position = LatLng(
                            gpsPoints.first().latitude,
                            gpsPoints.first().longitude
                        )
                    ),
                    title = "Départ",
                    snippet = "Début du voyage",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
                )
            }

            // Point actuel / dernier point
            if (gpsPoints.isNotEmpty()) {
                Marker(
                    state = MarkerState(
                        position = LatLng(
                            gpsPoints.last().latitude,
                            gpsPoints.last().longitude
                        )
                    ),
                    title = "Position actuelle",
                    snippet = "Dernier point GPS",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                )
            }

            // POIs
            pois.forEach { poi ->
                val lat = poi.latitude.toDoubleOrNull()
                val lon = poi.longitude.toDoubleOrNull()

                if (lat != null && lon != null) {
                    Marker(
                        state = MarkerState(position = LatLng(lat, lon)),
                        title = poi.name,
                        snippet = "${poi.type} • ${poi.address}",
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)
                    )
                }
            }
        }
    }
}