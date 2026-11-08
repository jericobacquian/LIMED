package com.example.weight_based_delivery_system

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.weight_based_delivery_system.ui.theme.Weight_based_delivery_systemTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Weight_based_delivery_systemTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    DeliveryDashboard(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun DeliveryDashboard(modifier: Modifier = Modifier) {
    // This is where your custom layout code will go!
    Text(
        text = "Weight-Based Delivery System Starter",
        modifier = modifier
    )
}
