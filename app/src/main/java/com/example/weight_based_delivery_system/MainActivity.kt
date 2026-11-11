package com.example.weight_based_delivery_system

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.createBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.weight_based_delivery_system.ui.theme.Weight_based_delivery_systemTheme
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView

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
    Box(modifier = modifier.fillMaxSize()) {
        MapLibreView(modifier = Modifier.fillMaxSize())

        // Branch & Delivery Barrier Info Overlay
        BranchInfoCard(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp),
            branchName = "YTO Express Nasugbu Branch",
            address = "300 J. P. Laurel St, Nasugbu, Batangas",
            coordinates = "14.0657415, 120.6334763",
            coverageRadiusKm = 20
        )
    }
}

@Composable
fun BranchInfoCard(
    modifier: Modifier = Modifier,
    branchName: String,
    address: String,
    coordinates: String,
    coverageRadiusKm: Int
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "Location",
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = branchName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = address,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "Coordinates: $coordinates",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Coverage Barrier",
                    tint = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Delivery Coverage: $coverageRadiusKm km Radius Barrier",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
fun MapLibreView(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // YTO Express Nasugbu Branch coordinates
    val pickupCenterLocation = remember { LatLng(14.0657415, 120.6334763) }
    val coverageRadiusKm = 20.0

    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
            getMapAsync { mapboxMap ->
                mapboxMap.setStyle("https://tiles.openfreemap.org/styles/liberty") {
                    // Zoom to show 20km radius barrier clearly
                    mapboxMap.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(pickupCenterLocation, 10.2),
                        2000
                    )

                    // 1. Draw 20km Outer Line Barrier ONLY (No fill color)
                    val circlePoints = createGeodesicCircle(pickupCenterLocation, coverageRadiusKm)
                    val polylineOptions = PolylineOptions()
                        .addAll(circlePoints)
                        .color(Color.parseColor("#1E88E5")) // Vibrant blue outer boundary line
                        .width(4.0f) // Line width
                    mapboxMap.addPolyline(polylineOptions)

                    // 2. Add Red Pin Marker natively on map
                    val markerIcon = IconFactory.getInstance(context).fromBitmap(createCustomMarkerBitmap())
                    mapboxMap.addMarker(
                        MarkerOptions()
                            .position(pickupCenterLocation)
                            .icon(markerIcon)
                            .title("YTO Express Nasugbu Branch")
                            .snippet("300 J. P. Laurel St, Nasugbu, Batangas")
                    )
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
 * Calculates a list of LatLng points forming a geodesic circle around a center point.
 */
private fun createGeodesicCircle(center: LatLng, radiusInKm: Double, pointsCount: Int = 128): List<LatLng> {
    val points = mutableListOf<LatLng>()
    val earthRadiusKm = 6371.0088
    val latRad = Math.toRadians(center.latitude)
    val lonRad = Math.toRadians(center.longitude)
    val dByR = radiusInKm / earthRadiusKm

    for (i in 0 until pointsCount) {
        val bearing = Math.toRadians((i * 360.0) / pointsCount)
        val pointLatRad = kotlin.math.asin(
            kotlin.math.sin(latRad) * kotlin.math.cos(dByR) +
                    kotlin.math.cos(latRad) * kotlin.math.sin(dByR) * kotlin.math.cos(bearing)
        )
        val pointLonRad = lonRad + kotlin.math.atan2(
            kotlin.math.sin(bearing) * kotlin.math.sin(dByR) * kotlin.math.cos(latRad),
            kotlin.math.cos(dByR) - kotlin.math.sin(latRad) * kotlin.math.sin(pointLatRad)
        )
        points.add(LatLng(Math.toDegrees(pointLatRad), Math.toDegrees(pointLonRad)))
    }
    if (points.isNotEmpty()) {
        points.add(points[0])
    }
    return points
}

/**
 * Creates a red pin marker Bitmap to be registered with MapLibre native annotations.
 */
private fun createCustomMarkerBitmap(): Bitmap {
    val width = 72
    val height = 96
    val bitmap = createBitmap(width, height)
    val canvas = Canvas(bitmap)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E53935") // Bright red
        style = Paint.Style.FILL
    }

    // Draw main pin head circle
    val headRadius = 26f
    val centerX = width / 2f
    val centerY = headRadius + 4f
    canvas.drawCircle(centerX, centerY, headRadius, paint)

    // Draw bottom pin pointer triangle
    val path = Path().apply {
        moveTo(centerX - headRadius + 4f, centerY + 10f)
        lineTo(centerX, height.toFloat() - 4f)
        lineTo(centerX + headRadius - 4f, centerY + 10f)
        close()
    }
    canvas.drawPath(path, paint)

    // Draw inner white circle
    paint.color = Color.WHITE
    canvas.drawCircle(centerX, centerY, 10f, paint)

    return bitmap
}