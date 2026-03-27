package com.example.smarttrip

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SmartTripApp()
        }
    }
}

@Composable
fun SmartTripApp() {
    var status by remember { mutableStateOf("Smart Trip") }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(text = status)

        Spacer(modifier = Modifier.height(20.dp))

        Button(onClick = {
            status = "Trip started 🚀"
        }) {
            Text("Start Trip")
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(onClick = {
            status = "Trip stopped 🛑"
        }) {
            Text("Stop Trip")
        }
    }
}