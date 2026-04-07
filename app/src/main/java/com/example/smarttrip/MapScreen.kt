package com.example.smarttrip

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*

@Composable
fun MapScreen(
    tripName: String,
    gpsPoints: List<GpsPoint>,
    pois: List<PoiPoint>,
    onBackClick: () -> Unit
) {
    val defaultLocation = LatLng(48.8566, 2.3522)
    val firstPoint = if (gpsPoints.isNotEmpty()) {
        LatLng(gpsPoints.first().latitude, gpsPoints.first().longitude)
    } else {
        defaultLocation
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(firstPoint, 13f)
    }

    Column(modifier = Modifier.fillMaxSize()) {
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
                Text(
                    text = "${gpsPoints.size} points GPS · ${pois.size} POIs",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
            OutlinedButton(
                onClick = onBackClick,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Retour")
            }
        }

        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState
        ) {
            // Trajet GPS
            if (gpsPoints.size >= 2) {
                Polyline(
                    points = gpsPoints.map { LatLng(it.latitude, it.longitude) },
                    color = Color(0xFF1F3C88),
                    width = 8f
                )
            }

            // Marqueur départ
            if (gpsPoints.isNotEmpty()) {
                Marker(
                    state = MarkerState(
                        position = LatLng(
                            gpsPoints.first().latitude,
                            gpsPoints.first().longitude
                        )
                    ),
                    title = "Depart",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
                )
                // Marqueur position actuelle
                Marker(
                    state = MarkerState(
                        position = LatLng(
                            gpsPoints.last().latitude,
                            gpsPoints.last().longitude
                        )
                    ),
                    title = "Position actuelle",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)
                )
            }

            // Marqueurs POIs
            pois.forEach { poi ->
                if (poi.latitude.isNotEmpty() && poi.longitude.isNotEmpty()) {
                    val lat = poi.latitude.toDoubleOrNull()
                    val lon = poi.longitude.toDoubleOrNull()
                    if (lat != null && lon != null) {
                        Marker(
                            state = MarkerState(position = LatLng(lat, lon)),
                            title = poi.name,
                            snippet = poi.type,
                            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)
                        )
                    }
                }
            }
        }
    }
}