package com.example.smarttrip

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

data class TripSummary(
    val id: String = "",
    val name: String = "",
    val createdAt: Long = 0,
    val gpsCount: Int = 0,
    val poiCount: Int = 0
)

@Composable
fun HistoryScreen(
    onBackClick: () -> Unit,
    onTripClick: (TripSummary) -> Unit
) {
    val database = FirebaseDatabase.getInstance("https://smarttrip-50485-default-rtdb.europe-west1.firebasedatabase.app")
    var trips by remember { mutableStateOf<List<TripSummary>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val ref = database.getReference("trips")
        ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<TripSummary>()
                for (tripSnap in snapshot.children) {
                    val id = tripSnap.key ?: continue
                    val name = tripSnap.child("info/name").getValue(String::class.java) ?: "Voyage"
                    val createdAt = tripSnap.child("info/createdAt").getValue(Long::class.java) ?: 0L
                    val gpsCount = tripSnap.child("gps_points").childrenCount.toInt()
                    val poiCount = tripSnap.child("pois").childrenCount.toInt()
                    list.add(TripSummary(id, name, createdAt, gpsCount, poiCount))
                }
                trips = list.sortedByDescending { it.createdAt }
                isLoading = false
            }

            override fun onCancelled(error: DatabaseError) {
                isLoading = false
            }
        })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row {
                    Text(
                        text = "Smart",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1F3C88)
                    )
                    Text(
                        text = "Trip",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF7043)
                    )
                }
                Text(
                    text = "Mes voyages",
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

        Spacer(modifier = Modifier.height(20.dp))

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF1F3C88))
            }
        } else if (trips.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Aucun voyage enregistré",
                    fontSize = 16.sp,
                    color = Color.Gray
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(trips) { trip ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTripClick(trip) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = trip.name,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1F3C88)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text(
                                    text = "${trip.gpsCount} points GPS",
                                    fontSize = 13.sp,
                                    color = Color.Gray
                                )
                                Text(
                                    text = "${trip.poiCount} POIs",
                                    fontSize = 13.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}