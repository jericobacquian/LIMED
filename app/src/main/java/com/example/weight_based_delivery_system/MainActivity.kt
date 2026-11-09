package com.example.weight_based_delivery_system

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.createBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.weight_based_delivery_system.ui.theme.Weight_based_delivery_systemTheme
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.plugins.annotation.SymbolManager
import org.maplibre.android.plugins.annotation.SymbolOptions

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize MapLibre before rendering UI
        MapLibre.getInstance(this)

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
    MapLibreView(modifier = modifier)
}

@Composable
fun MapLibreView(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val pickupCenterLocation = remember { LatLng(14.0735, 120.6322) }

    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
            getMapAsync { mapboxMap ->
                mapboxMap.setStyle("https://tiles.openfreemap.org/styles/liberty") { style ->
                    // Add a custom marker icon to style sprite registry
                    val markerBitmap = createCustomMarkerBitmap()
                    style.addImage("custom-pickup-marker", markerBitmap)

                    mapboxMap.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(pickupCenterLocation, 11.0),
                        2000
                    )

                    val symbolManager = SymbolManager(this, mapboxMap, style)
                    symbolManager.iconAllowOverlap = true
                    symbolManager.textAllowOverlap = true

                    val symbolOptions = SymbolOptions()
                        .withLatLng(pickupCenterLocation)
                        .withIconImage("custom-pickup-marker")
                        .withIconSize(1.0f)
                        .withTextField("Pickup Hub")
                        .withTextOffset(arrayOf(0f, 1.2f))
                        .withTextColor("#D32F2F")
                        .withTextSize(14f)

                    symbolManager.create(symbolOptions)
                }
            }
        }
    }

    // Manage MapView lifecycle inside Jetpack Compose
    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        // Catch initial lifecycle state if already created/started/resumed
        val currentState = lifecycleOwner.lifecycle.currentState
        if (currentState.isAtLeast(Lifecycle.State.STARTED)) {
            mapView.onStart()
        }
        if (currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            mapView.onResume()
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    // Embed MapView into Compose UI
    AndroidView(
        factory = { mapView },
        modifier = modifier.fillMaxSize()
    )
}

/**
 * Creates a pin marker Bitmap to be registered with MapLibre style.
 */
private fun createCustomMarkerBitmap(): Bitmap {
    val width = 64
    val height = 64
    val bitmap = createBitmap(width, height)
    val canvas = Canvas(bitmap)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.FILL
    }



    // Draw red circle marker
    canvas.drawCircle(width / 2f, height / 2f, 20f, paint)

    // Draw inner white dot
    paint.color = Color.WHITE
    canvas.drawCircle(width / 2f, height / 2f, 8f, paint)

    return bitmap
}