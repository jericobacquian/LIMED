package com.example.weight_based_delivery_system

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.util.LruCache
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.createBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.weight_based_delivery_system.data.DeliveryDriver
import com.example.weight_based_delivery_system.data.DeterministicRejectionException
import com.example.weight_based_delivery_system.data.DeliveryRepository
import com.example.weight_based_delivery_system.data.DeliveryStatus
import com.example.weight_based_delivery_system.data.DriverShiftState
import com.example.weight_based_delivery_system.data.OsrmConfig
import com.example.weight_based_delivery_system.data.OsrmRoutingService
import com.example.weight_based_delivery_system.data.ParcelItem
import com.example.weight_based_delivery_system.data.RiderState
import com.example.weight_based_delivery_system.data.RouteCacheManager
import com.example.weight_based_delivery_system.data.RouteFetchStatus
import com.example.weight_based_delivery_system.data.VehicleType
import com.example.weight_based_delivery_system.data.calculateDistanceKm
import com.example.weight_based_delivery_system.data.calculateParcelEta
import com.example.weight_based_delivery_system.data.describePosition
import com.example.weight_based_delivery_system.data.getTrafficFactor
import com.example.weight_based_delivery_system.data.interpolateAlongRoute
import com.example.weight_based_delivery_system.data.osrmProfile
import com.example.weight_based_delivery_system.ui.theme.Weight_based_delivery_systemTheme
import kotlinx.coroutines.delay
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.Polyline
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

private var activeSimLoopCount = 0

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize MapLibre before rendering UI
        MapLibre.getInstance(this)

        if (OsrmConfig.CLEAR_CACHE_ON_START) {
            RouteCacheManager.clear()
        }

        setContent {
            Weight_based_delivery_systemTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    DeliveryDashboard(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

const val SHOW_GENERATOR_BUTTON = true

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeliveryDashboard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val hubLat = 14.0657415
    val hubLng = 120.6334763

    // State for Daily Dispatch Parcels (Per-rider sampling: 40 to 70 items per rider)
    val dailyDispatchParcels = remember { mutableStateListOf<ParcelItem>() }
    var currentRunSeed by remember { mutableStateOf(DeliveryRepository.DEMO_SEED ?: System.currentTimeMillis()) }
    var runId by remember { mutableStateOf(0L) }

    // Load Drivers AFTER repository master pool is populated
    val drivers = remember(dailyDispatchParcels.size) { DeliveryRepository.getDrivers(context) }

    // Multi-Parcel Sequential Driver Shift States
    val driverShiftStates = remember(dailyDispatchParcels.size, drivers.size) {
        mutableStateListOf<DriverShiftState>().apply {
            drivers.forEach { driver ->
                val queue = dailyDispatchParcels.filter { it.assignedDriver.id == driver.id }
                add(
                    DriverShiftState(
                        driver = driver,
                        parcelQueue = queue
                    )
                )
            }
        }
    }

    // Selected Driver Focus State (Default to Driver 1 - Mark Santos)
    var selectedDriverId by remember { mutableStateOf(drivers.firstOrNull()?.id ?: "") }
    val activeSelectedShift = remember(selectedDriverId, driverShiftStates.size) {
        driverShiftStates.firstOrNull { it.driver.id == selectedDriverId }
    }

    // Central 6:00 AM Dispatch Simulation Clock & Multiplier
    var simSecondsElapsed by remember { mutableIntStateOf(0) } // Seconds past 06:00 AM
    var simSpeedMultiplier by remember { mutableIntStateOf(30) } // 30x default fast-forward speed
    var isPaused by remember { mutableStateOf(false) }

    // Rush Hour Traffic Indicator (07:30 AM - 09:00 AM)
    val isRushHour = remember(simSecondsElapsed) {
        simSecondsElapsed in 5400..10800 // 07:30 AM to 09:00 AM
    }

    // UI Panel Toggles for Right Side Vertical Buttons
    var showDriverInfoPanel by remember { mutableStateOf(false) }
    var showBranchInfoPanel by remember { mutableStateOf(false) }

    var isBottomSheetOpen by remember { mutableStateOf(false) }
    var detailParcelItem by remember { mutableStateOf<ParcelItem?>(null) }

    fun refreshDailyDispatch() {
        val newSeed = DeliveryRepository.DEMO_SEED ?: System.currentTimeMillis()
        currentRunSeed = newSeed
        runId++
        Log.d("DeliverySim", "Refreshing simulation. Run seed: $newSeed, Run ID: $runId")

        simSecondsElapsed = 0
        isPaused = false
        showDriverInfoPanel = false
        showBranchInfoPanel = false
        isBottomSheetOpen = false
        detailParcelItem = null

        dailyDispatchParcels.clear()
        val newDispatch = DeliveryRepository.generateDailyDispatch(context, newSeed)
        dailyDispatchParcels.addAll(newDispatch)

        driverShiftStates.clear()
        drivers.forEach { driver ->
            val queue = dailyDispatchParcels.filter { it.assignedDriver.id == driver.id }
            driverShiftStates.add(
                DriverShiftState(
                    driver = driver,
                    parcelQueue = queue
                )
            )
        }
        selectedDriverId = drivers.firstOrNull()?.id ?: ""
    }

    LaunchedEffect(Unit) {
        if (dailyDispatchParcels.isEmpty()) {
            refreshDailyDispatch()
        }
    }

    // Asynchronously fetch OSRM turn-by-turn road routes for each rider's active leg with retry & backoff
    driverShiftStates.forEach { shiftState ->
        val parcel = shiftState.currentParcel
        val destLat = parcel?.destinationLat ?: hubLat
        val destLng = parcel?.destinationLng ?: hubLng
        val legName = if (parcel != null) "Stop #${shiftState.currentParcelIndex + 1} (${parcel.name})" else "Return to Hub"

        val currentRunId = runId
        LaunchedEffect(runId, shiftState.driver.id, shiftState.currentParcelIndex, shiftState.riderState) {
            if (runId != currentRunId) return@LaunchedEffect
            if (shiftState.riderState == RiderState.EN_ROUTE || shiftState.riderState == RiderState.RETURNING) {
                var backoffMs = 1000L
                var attempt = 0
                var success = false

                while (!success && (shiftState.riderState == RiderState.EN_ROUTE || shiftState.riderState == RiderState.RETURNING)) {
                    if (runId != currentRunId) return@LaunchedEffect
                    attempt++
                    shiftState.retryCount = attempt
                    shiftState.routeStatus = RouteFetchStatus.LOADING
                    shiftState.routeSource = "Fetching (Attempt #$attempt)"

                    val result = OsrmRoutingService.fetchRoute(
                        driverName = shiftState.driver.name,
                        legName = legName,
                        startLat = shiftState.legStartLat,
                        startLng = shiftState.legStartLng,
                        destLat = destLat,
                        destLng = destLng,
                        vehicleType = shiftState.driver.vehicleType,
                        currentLat = shiftState.currentLat,
                        currentLng = shiftState.currentLng,
                        runId = runId
                    )

                    if (runId != currentRunId) return@LaunchedEffect

                    if (result.isSuccess) {
                        val route = result.getOrNull()
                        if (route != null && runId == currentRunId) {
                            shiftState.currentRoute = route
                            shiftState.routeStatus = RouteFetchStatus.READY
                            val isCache = RouteCacheManager.get(
                                com.example.weight_based_delivery_system.data.buildRouteCacheKey(
                                    shiftState.legStartLat, shiftState.legStartLng,
                                    destLat, destLng,
                                    shiftState.driver.vehicleType.osrmProfile
                                )
                            ) != null
                            shiftState.routeSource = if (isCache) "Cache (${route.points.size} pts)" else "OSRM (${route.points.size} pts)"
                            success = true
                        }
                    } else {
                        val exception = result.exceptionOrNull()
                        if (exception is DeterministicRejectionException && runId == currentRunId) {
                            val skipReason = if (exception.routeLengthMeters > 0.0) {
                                "unreachable: route %.1fkm for %.1fm straight line".format(exception.routeLengthMeters / 1000.0, exception.straightDistMeters)
                            } else {
                                "unreachable: ${exception.message}"
                            }
                            val stopInfo = if (parcel != null) "Stop #${shiftState.currentParcelIndex + 1} (${parcel.name})" else "Return Leg"
                            shiftState.skippedReasons.add("$stopInfo: $skipReason")

                            if (parcel != null) {
                                parcel.status = DeliveryStatus.SKIPPED
                            }
                            shiftState.routeStatus = RouteFetchStatus.LOADING
                            shiftState.currentRoute = null
                            shiftState.routeSource = "Skipped: $skipReason"
                            Log.w("RouteLogger", "[RouteLogger] Rider: ${shiftState.driver.name} | Leg: $legName | STOP SKIPPED: $skipReason")

                            // Advance rider to next stop from rider's current position
                            shiftState.legStartLat = shiftState.currentLat
                            shiftState.legStartLng = shiftState.currentLng
                            shiftState.legSecondsElapsed = 0.0
                            shiftState.currentParcelIndex += 1

                            if (shiftState.currentParcel != null) {
                                shiftState.riderState = RiderState.EN_ROUTE
                            } else {
                                shiftState.riderState = RiderState.RETURNING
                            }
                            success = true
                        }
                    }

                    if (!success && runId == currentRunId) {
                        val exception = result.exceptionOrNull()
                        val errReason = exception?.message ?: "Unknown error"
                        val isNetworkOrRateLimit = exception is java.net.SocketTimeoutException ||
                                exception is java.net.UnknownHostException ||
                                exception is java.net.ConnectException ||
                                errReason.contains("SocketTimeoutException", ignoreCase = true) ||
                                errReason.contains("UnknownHostException", ignoreCase = true) ||
                                errReason.contains("ConnectException", ignoreCase = true) ||
                                errReason.contains("HTTP 403", ignoreCase = true) ||
                                errReason.contains("HTTP 429", ignoreCase = true) ||
                                errReason.contains("HTTP 503", ignoreCase = true)

                        if (!isNetworkOrRateLimit && attempt >= 5) {
                            val capReason = "hard cap reached (failed 5 times: $errReason)"
                            val stopInfo = if (parcel != null) "Stop #${shiftState.currentParcelIndex + 1} (${parcel.name})" else "Return Leg"
                            shiftState.skippedReasons.add("$stopInfo: $capReason")
                            Log.w("RouteLogger", "[RouteLogger] HARD CAP REACHED (5 retries) | Rider: ${shiftState.driver.name} | Leg: $legName | Reason: $errReason")

                            if (parcel != null) {
                                parcel.status = DeliveryStatus.SKIPPED
                                shiftState.routeStatus = RouteFetchStatus.LOADING
                                shiftState.currentRoute = null
                                shiftState.routeSource = "Skipped: $capReason"

                                shiftState.legStartLat = shiftState.currentLat
                                shiftState.legStartLng = shiftState.currentLng
                                shiftState.legSecondsElapsed = 0.0
                                shiftState.currentParcelIndex += 1

                                if (shiftState.currentParcel != null) {
                                    shiftState.riderState = RiderState.EN_ROUTE
                                } else {
                                    shiftState.riderState = RiderState.RETURNING
                                }
                            } else {
                                shiftState.riderState = RiderState.SHIFT_COMPLETED
                                shiftState.currentLat = hubLat
                                shiftState.currentLng = hubLng
                                shiftState.routeStatus = RouteFetchStatus.UNAVAILABLE
                                shiftState.routeSource = "Completed (Return Leg Failed 5x)"
                            }
                            success = true
                            break
                        }

                        shiftState.routeStatus = RouteFetchStatus.UNAVAILABLE
                        shiftState.routeSource = if (isNetworkOrRateLimit) "No connection, retrying (#$attempt)" else "Unavailable: [$errReason] (Retry #$attempt in ${backoffMs / 1000}s)"
                        Log.w("RouteLogger", "[RouteLogger] Rider: ${shiftState.driver.name} | Leg: $legName | Status: Unavailable | Reason: $errReason | Retry #$attempt in ${backoffMs / 1000}s")
                        delay(backoffMs)
                        backoffMs = (backoffMs * 2).coerceAtMost(30000L)
                    }
                }
            }
        }
    }

    /**
     * Purpose: Central 6:00 AM Real-World Simulation Engine tick loop executing every 100 ms.
     * Main Steps:
     * 1. Advance simulation clock (simSecondsElapsed) and stepAccumulator based on simSpeedMultiplier.
     * 2. Evaluate rush-hour traffic factor (getTrafficFactor) during 07:30 AM - 09:00 AM window.
     * 3. Iterate through active driverShiftStates to update RiderState:
     *    - AT_HUB: Transition to EN_ROUTE when parcel is available.
     *    - EN_ROUTE: Move rider along OSRM route geometry using interpolateAlongRoute; transition to UNLOADING on arrival.
     *    - UNLOADING: Countdown unloadingSecondsRemaining (1.5m, 6m, 9m); mark DELIVERED when complete and advance queue.
     *    - RETURNING: Move rider back to Nasugbu Hub; transition to SHIFT_COMPLETED on arrival.
     * 4. Check if all riders completed shift -> auto-pause simulation engine.
     */
    LaunchedEffect(runId, isPaused, simSpeedMultiplier, dailyDispatchParcels.size) {
        activeSimLoopCount++
        Log.d("SimEngine", "Started simulation loop. Active loop count: $activeSimLoopCount")

        var stepAccumulator = 0.0

        try {
            while (!isPaused) {
                delay(100)
                val stepSeconds = (simSpeedMultiplier * 0.1).coerceAtMost(10.0)
                stepAccumulator += stepSeconds
                if (stepAccumulator >= 1.0) {
                    val wholeSecs = stepAccumulator.toInt()
                    simSecondsElapsed += wholeSecs
                    stepAccumulator -= wholeSecs
                }

                // Traffic Factor: 25% speed reduction during 07:30 AM - 09:00 AM Rush Hour
                val trafficFactor = getTrafficFactor(simSecondsElapsed)

                driverShiftStates.forEach { shiftState ->
                    val parcel = shiftState.currentParcel

                    when (shiftState.riderState) {
                        RiderState.AT_HUB -> {
                            shiftState.currentLat = hubLat
                            shiftState.currentLng = hubLng
                            if (parcel != null) {
                                shiftState.riderState = RiderState.EN_ROUTE
                                shiftState.legStartLat = hubLat
                                shiftState.legStartLng = hubLng
                                shiftState.legSecondsElapsed = 0.0
                            }
                        }

                        RiderState.EN_ROUTE -> {
                            if (parcel != null) {
                                parcel.status = DeliveryStatus.IN_TRANSIT

                                val route = shiftState.currentRoute
                                if (shiftState.routeStatus == RouteFetchStatus.READY && route != null) {
                                    val effectiveSpeedKmH = shiftState.driver.vehicleType.avgSpeedKmH * trafficFactor
                                    val speedMetersPerSec = (effectiveSpeedKmH * 1000.0) / 3600.0

                                    shiftState.legSecondsElapsed += stepSeconds
                                    val distMeters = shiftState.legSecondsElapsed * speedMetersPerSec

                                    val currPos = interpolateAlongRoute(route, distMeters)
                                    shiftState.currentLat = currPos.latitude
                                    shiftState.currentLng = currPos.longitude
                                    parcel.currentLat = currPos.latitude
                                    parcel.currentLng = currPos.longitude

                                    if (distMeters >= route.totalDistanceMeters) {
                                        // Arrived at destination -> Pin position and transition to UNLOADING
                                        shiftState.currentLat = parcel.destinationLat
                                        shiftState.currentLng = parcel.destinationLng
                                        parcel.currentLat = parcel.destinationLat
                                        parcel.currentLng = parcel.destinationLng
                                        shiftState.riderState = RiderState.UNLOADING
                                        shiftState.unloadingSecondsRemaining = parcel.unloadingDurationSeconds
                                    }
                                } else {
                                    // Route is loading or unavailable -> Stay stationary at leg start!
                                    shiftState.currentLat = shiftState.legStartLat
                                    shiftState.currentLng = shiftState.legStartLng
                                    parcel.currentLat = shiftState.legStartLat
                                    parcel.currentLng = shiftState.legStartLng
                                }
                            } else {
                                shiftState.riderState = RiderState.RETURNING
                                shiftState.legSecondsElapsed = 0.0
                            }
                        }

                        RiderState.UNLOADING -> {
                            if (parcel != null) {
                                // Position MUST stay strictly pinned during unloading
                                shiftState.currentLat = parcel.destinationLat
                                shiftState.currentLng = parcel.destinationLng
                                parcel.currentLat = parcel.destinationLat
                                parcel.currentLng = parcel.destinationLng

                                shiftState.unloadingSecondsRemaining -= stepSeconds
                                if (shiftState.unloadingSecondsRemaining <= 0) {
                                    parcel.status = DeliveryStatus.DELIVERED

                                    // Store previous stop's coordinates as legStartLat/Lng for next leg
                                    shiftState.legStartLat = parcel.destinationLat
                                    shiftState.legStartLng = parcel.destinationLng
                                    shiftState.legSecondsElapsed = 0.0
                                    shiftState.currentParcelIndex += 1

                                    if (shiftState.currentParcel != null) {
                                        shiftState.riderState = RiderState.EN_ROUTE
                                    } else {
                                        shiftState.riderState = RiderState.RETURNING
                                    }
                                }
                            } else {
                                shiftState.riderState = RiderState.RETURNING
                                shiftState.legSecondsElapsed = 0.0
                            }
                        }

                        RiderState.RETURNING -> {
                            val route = shiftState.currentRoute
                            if (shiftState.routeStatus == RouteFetchStatus.READY && route != null) {
                                val effectiveSpeedKmH = shiftState.driver.vehicleType.avgSpeedKmH * trafficFactor
                                val speedMetersPerSec = (effectiveSpeedKmH * 1000.0) / 3600.0

                                shiftState.legSecondsElapsed += stepSeconds
                                val distMeters = shiftState.legSecondsElapsed * speedMetersPerSec

                                val currPos = interpolateAlongRoute(route, distMeters)
                                shiftState.currentLat = currPos.latitude
                                shiftState.currentLng = currPos.longitude

                                if (distMeters >= route.totalDistanceMeters) {
                                    shiftState.currentLat = hubLat
                                    shiftState.currentLng = hubLng
                                    shiftState.riderState = RiderState.SHIFT_COMPLETED
                                }
                            } else {
                                shiftState.currentLat = shiftState.legStartLat
                                shiftState.currentLng = shiftState.legStartLng
                            }
                        }

                        RiderState.SHIFT_COMPLETED -> {
                            shiftState.currentLat = hubLat
                            shiftState.currentLng = hubLng
                        }
                    }
                }

                // Check if ALL drivers have completed their shift!
                val allCompleted = driverShiftStates.isNotEmpty() && driverShiftStates.all {
                    it.riderState == RiderState.SHIFT_COMPLETED
                }
                if (allCompleted) {
                    isPaused = true
                    Log.d("SimEngine", "🎉 ALL SHIFTS COMPLETE! All 5 Riders Returned to Base. Simulation engine auto-paused.")
                }
            }
        } finally {
            activeSimLoopCount--
            Log.d("SimEngine", "Stopped simulation loop. Remaining active loop count: $activeSimLoopCount")
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Lightweight Map View displaying 5 moving vehicles + ONLY selected driver's active leg road route line
        MapLibreView(
            modifier = Modifier.fillMaxSize(),
            driverShiftStates = driverShiftStates,
            selectedDriverId = selectedDriverId,
            simSecondsElapsed = simSecondsElapsed,
            runId = runId
        )

        // 🌅 SLEEK INTEGRATED TOP FLOATING HEADER PILL BAR
        SleekHeaderPillBar(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 36.dp, start = 16.dp, end = 16.dp),
            simSecondsElapsed = simSecondsElapsed,
            speedMultiplier = simSpeedMultiplier,
            isPaused = isPaused,
            isRushHour = isRushHour,
            activeShift = activeSelectedShift,
            runSeed = currentRunSeed,
            onTogglePause = { isPaused = !isPaused },
            onCycleSpeed = {
                simSpeedMultiplier = when (simSpeedMultiplier) {
                    1 -> 10
                    10 -> 30
                    30 -> 60
                    else -> 1
                }
            }
        )

        // Pop-Over Panels (Only appear when user clicks the dedicated right-side buttons)
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 80.dp, start = 16.dp)
                .fillMaxWidth(0.85f)
        ) {
            AnimatedVisibility(visible = showDriverInfoPanel, enter = fadeIn(), exit = fadeOut()) {
                activeSelectedShift?.let { shift ->
                    ActiveDriverStatusCard(
                        shiftState = shift,
                        onClose = { showDriverInfoPanel = false }
                    )
                }
            }

            if (showDriverInfoPanel && showBranchInfoPanel) Spacer(modifier = Modifier.height(8.dp))

            AnimatedVisibility(visible = showBranchInfoPanel, enter = fadeIn(), exit = fadeOut()) {
                BranchInfoCard(
                    branchName = "YTO Express Nasugbu Branch",
                    address = "300 J. P. Laurel St, Nasugbu, Batangas",
                    coordinates = "14.0657415, 120.6334763",
                    coverageRadiusKm = 20,
                    onClose = { showBranchInfoPanel = false }
                )
            }
        }

        // RIGHT-MOST SIDE VERTICAL FLOATING ACTION BUTTON BAR
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.End
        ) {
            // 0. Debug Generator Button (Run Once)
            if (SHOW_GENERATOR_BUTTON) {
                SmallFloatingActionButton(
                    onClick = {
                        coroutineScope.launch {
                            DeliveryRepository.generateAndSaveCustomerLocations(context)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ) {
                    Icon(Icons.Default.Info, contentDescription = "Gen Locations")
                }
            }

            // 1. Refresh / New Delivery Day Button
            SmallFloatingActionButton(
                onClick = {
                    simSecondsElapsed = 0
                    refreshDailyDispatch()
                },
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "New Delivery Day")
            }

            // 2. Active Driver Info Panel Button
            SmallFloatingActionButton(
                onClick = {
                    showDriverInfoPanel = !showDriverInfoPanel
                    if (showDriverInfoPanel) {
                        showBranchInfoPanel = false
                    }
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Default.Person, contentDescription = "Driver Info")
            }

            // 3. Nasugbu Branch Hub Info Button
            SmallFloatingActionButton(
                onClick = {
                    showBranchInfoPanel = !showBranchInfoPanel
                    if (showBranchInfoPanel) {
                        showDriverInfoPanel = false
                    }
                },
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ) {
                Icon(Icons.Default.Place, contentDescription = "Branch Info")
            }

            // 4. Browse Daily Parcels Catalog Button
            FloatingActionButton(
                onClick = { isBottomSheetOpen = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, contentDescription = "Browse")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Parcels (${dailyDispatchParcels.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        // Bottom Bar: Driver Focus Selector Bar
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            DriverSelectorBar(
                drivers = drivers,
                selectedDriverId = selectedDriverId,
                onSelectDriver = { selectedDriverId = it }
            )
        }

        // Bottom Sheet: Daily Parcels Catalog
        if (isBottomSheetOpen) {
            ModalBottomSheet(
                onDismissRequest = { isBottomSheetOpen = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                ParcelTrackerSheetContent(
                    parcels = dailyDispatchParcels,
                    driverShiftStates = driverShiftStates,
                    simSecondsElapsed = simSecondsElapsed,
                    selectedParcel = detailParcelItem,
                    onSelectParcel = { parcel ->
                        detailParcelItem = parcel
                    },
                    onFocusDriverOnMap = { driverId ->
                        selectedDriverId = driverId
                        isBottomSheetOpen = false
                        showDriverInfoPanel = true
                    },
                    onClose = { isBottomSheetOpen = false }
                )
            }
        }
    }
}

const val SHOW_SEED = true

@Composable
fun SleekHeaderPillBar(
    modifier: Modifier = Modifier,
    simSecondsElapsed: Int,
    speedMultiplier: Int,
    isPaused: Boolean,
    isRushHour: Boolean,
    activeShift: DriverShiftState?,
    runSeed: Long,
    onTogglePause: () -> Unit,
    onCycleSpeed: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    val totalMins = simSecondsElapsed / 60
    val totalMinsFromMidnight = 360 + totalMins // 6:00 AM start
    val totalHoursFromMidnight = (totalMinsFromMidnight / 60) % 24
    val mins = totalMinsFromMidnight % 60
    val hour12 = when {
        totalHoursFromMidnight == 0 -> 12
        totalHoursFromMidnight > 12 -> totalHoursFromMidnight - 12
        else -> totalHoursFromMidnight
    }
    val amPm = if (totalHoursFromMidnight < 12) "AM" else "PM"
    val clockStr = "%02d:%02d %s".format(hour12, mins, amPm)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
        shadowElevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Row 1: Clock (left), Active Driver Name (center, weight 1f), Speed & Close Buttons (right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Clock & Traffic Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "🌅 $clockStr",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isRushHour) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "🚦 Rush",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Active Driver Name (weight 1f, Ellipsis)
                activeShift?.let { shift ->
                    Text(
                        text = "🚚 ${shift.driver.name}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                    )
                } ?: Spacer(modifier = Modifier.weight(1f))

                // Speed Button (min 40dp touch target)
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .clickable { onCycleSpeed() }
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .heightIn(min = 40.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Speed",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${speedMultiplier}x",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // Pause / Close Button (min 40dp touch target)
                Box(
                    modifier = Modifier.size(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = onTogglePause,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Close,
                            contentDescription = "Pause/Play",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Row 2 (Secondary): Seed info (tappable to copy)
            if (SHOW_SEED) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(runSeed.toString()))
                            android.widget.Toast.makeText(context, "Seed copied", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🌱 Seed: $runSeed (Tap to copy)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun BranchInfoCard(
    modifier: Modifier = Modifier,
    branchName: String,
    address: String,
    coordinates: String,
    coverageRadiusKm: Int,
    onClose: () -> Unit
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
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Location",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = branchName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.outline)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = address,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "Coordinates: $coordinates",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Coverage Barrier",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Delivery Coverage: $coverageRadiusKm km Radius Barrier",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
fun ActiveDriverStatusCard(
    shiftState: DriverShiftState,
    onClose: () -> Unit
) {
    val parcel = shiftState.currentParcel
    val totalQueueCount = shiftState.parcelQueue.size
    val currentStopNum = shiftState.currentParcelIndex + 1
    var isQueueExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header Row: Driver Name, Vehicle, License Plate
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Driver",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "${shiftState.driver.name} (${shiftState.driver.vehicleName})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = shiftState.driver.getSummary(),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.wrapContentWidth()
                ) {
                    Text(
                        text = shiftState.driver.vehicleType.name,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (shiftState.routeStatus == RouteFetchStatus.LOADING) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "⏳ Fetching OSRM Turn-by-Turn Road Route...",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            } else if (shiftState.routeStatus == RouteFetchStatus.UNAVAILABLE) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "⚠️ ${shiftState.routeSource}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            } else if (shiftState.isUnloading && parcel != null) {
                // 📦 Unloading Delay Banner
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📦 Unloading & Handover: ${(shiftState.unloadingSecondsRemaining / 60).toInt() + 1} mins remaining at ${parcel.customerName}'s address",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            } else if (shiftState.isReturningToHub) {
                // 🏠 Return Trip to Hub Banner
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "🏠 Return Leg: Driving back to YTO Express Nasugbu Hub...",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            } else if (parcel != null) {
                // Active Leg Tracking Info
                Text(
                    text = "🚚 Active Leg: Rider Position ➔ Stop #$currentStopNum (${parcel.name} - ₱%,.2f)".format(parcel.pricePhp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    softWrap = false
                )

                Text(
                    text = "📍 Target Destination: ${parcel.destinationAddress} (${parcel.customerName})",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                    maxLines = 1,
                    softWrap = false
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Payload: %.1f kg | Skipped: ${shiftState.skippedCount}".format(shiftState.currentPayloadWeightKg),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "⏱️ Leg ETA: ${parcel.estimatedTimeMinutes} mins",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "🌐 Route Source: ${shiftState.routeSource}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                if (shiftState.skippedReasons.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .padding(6.dp)
                    ) {
                        Text(
                            text = "⚠️ Skipped Stops Reasons (${shiftState.skippedReasons.size}):",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        shiftState.skippedReasons.forEach { reason ->
                            Text(
                                text = " • $reason",
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                maxLines = 2
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "🎉 SHIFT COMPLETE! Returned to Nasugbu Hub.",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Shift Progress Bar
            val progressRatio = (shiftState.deliveredCount.toFloat() / totalQueueCount.coerceAtLeast(1))
            LinearProgressIndicator(
                progress = { progressRatio },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Toggle Button for Rider's Ordered Parcel Queue
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isQueueExpanded = !isQueueExpanded }
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📋 Delivery Queue ($totalQueueCount stops) - Heaviest First",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (isQueueExpanded) "▲ Hide" else "▼ View Order",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (isQueueExpanded) {
                Spacer(modifier = Modifier.height(4.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .background(
                            MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(6.dp)
                ) {
                    itemsIndexed(shiftState.parcelQueue) { index, item ->
                        val stopNum = index + 1
                        val statusIcon = when (item.status) {
                            DeliveryStatus.DELIVERED -> "✅"
                            DeliveryStatus.IN_TRANSIT -> "🚚"
                            DeliveryStatus.PENDING -> "⏳"
                            DeliveryStatus.SKIPPED -> "⚠️"
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp, horizontal = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "$statusIcon Stop #$stopNum: ${item.name} (${item.weightKg} kg)",
                                fontSize = 11.sp,
                                fontWeight = if (index == shiftState.currentParcelIndex) FontWeight.Bold else FontWeight.Normal,
                                color = if (index == shiftState.currentParcelIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = if (item.status == DeliveryStatus.DELIVERED) "Delivered" else "%.1f km".format(item.distanceFromHubKm),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DriverSelectorBar(
    drivers: List<DeliveryDriver>,
    selectedDriverId: String,
    onSelectDriver: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp
    ) {
        LazyRow(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(drivers) { driver ->
                val isSelected = selectedDriverId == driver.id
                Surface(
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .clickable { onSelectDriver(driver.id) }
                        .then(
                            if (isSelected) Modifier.border(
                                width = 1.5.dp,
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(16.dp)
                            ) else Modifier
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when (driver.vehicleType) {
                                VehicleType.MOTORCYCLE -> "🏍️"
                                VehicleType.L300 -> "🚐"
                                VehicleType.TRUCK -> "🚚"
                            },
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = driver.name.split(" ").first(),
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ParcelTrackerSheetContent(
    parcels: List<ParcelItem>,
    driverShiftStates: List<DriverShiftState>,
    simSecondsElapsed: Int,
    selectedParcel: ParcelItem?,
    onSelectParcel: (ParcelItem) -> Unit,
    onFocusDriverOnMap: (String) -> Unit,
    onClose: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedVehicleFilter by remember { mutableStateOf<VehicleType?>(null) }
    var currentDetailItem by remember { mutableStateOf(selectedParcel) }

    val trafficFactor = getTrafficFactor(simSecondsElapsed)

    val filteredParcels = remember(searchQuery, selectedVehicleFilter, parcels) {
        parcels.filter { item ->
            val matchesSearch = item.id.contains(searchQuery, ignoreCase = true) ||
                    item.name.contains(searchQuery, ignoreCase = true) ||
                    item.customerName.contains(searchQuery, ignoreCase = true) ||
                    item.destinationAddress.contains(searchQuery, ignoreCase = true) ||
                    item.brand.contains(searchQuery, ignoreCase = true) ||
                    item.assignedDriver.name.contains(searchQuery, ignoreCase = true)
            val matchesFilter = selectedVehicleFilter == null || item.assignedDriver.vehicleType == selectedVehicleFilter
            matchesSearch && matchesFilter
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f)
            .padding(16.dp)
    ) {
        if (currentDetailItem != null) {
            val shiftState = driverShiftStates.firstOrNull { it.driver.id == currentDetailItem!!.assignedDriver.id }
            // Detailed View of Selected Parcel
            ParcelDetailView(
                parcel = currentDetailItem!!,
                shiftState = shiftState,
                simSecondsElapsed = simSecondsElapsed,
                trafficFactor = trafficFactor,
                onBack = { currentDetailItem = null },
                onFocusDriverOnMap = {
                    onFocusDriverOnMap(currentDetailItem!!.assignedDriver.id)
                }
            )
        } else {
            // List View & Filters
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Daily Dispatch Catalog (${filteredParcels.size} Parcels)",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search by ID, parcel, customer, area...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Vehicle Type Filter Chips in a horizontal scrollable row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedVehicleFilter == null,
                    onClick = { selectedVehicleFilter = null },
                    label = { Text("All (${parcels.size})", maxLines = 1, softWrap = false) }
                )
                FilterChip(
                    selected = selectedVehicleFilter == VehicleType.MOTORCYCLE,
                    onClick = { selectedVehicleFilter = VehicleType.MOTORCYCLE },
                    label = { Text("🏍️ Motorcycle", maxLines = 1, softWrap = false) }
                )
                FilterChip(
                    selected = selectedVehicleFilter == VehicleType.L300,
                    onClick = { selectedVehicleFilter = VehicleType.L300 },
                    label = { Text("🚐 L300", maxLines = 1, softWrap = false) }
                )
                FilterChip(
                    selected = selectedVehicleFilter == VehicleType.TRUCK,
                    onClick = { selectedVehicleFilter = VehicleType.TRUCK },
                    label = { Text("🚚 Truck", maxLines = 1, softWrap = false) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Parcel List
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(filteredParcels) { parcel ->
                    val shiftState = driverShiftStates.firstOrNull { it.driver.id == parcel.assignedDriver.id }
                    ParcelItemCard(
                        parcel = parcel,
                        shiftState = shiftState,
                        onClick = {
                            onSelectParcel(parcel)
                            currentDetailItem = parcel
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ParcelItemCard(
    parcel: ParcelItem,
    shiftState: DriverShiftState?,
    onClick: () -> Unit
) {
    val statusChipText: String
    val statusChipColor: androidx.compose.ui.graphics.Color
    val statusContentColor: androidx.compose.ui.graphics.Color

    val isUnloadingCurrent = shiftState?.isUnloading == true && shiftState.currentParcel?.id == parcel.id

    when {
        parcel.status == DeliveryStatus.DELIVERED -> {
            statusChipText = "Delivered"
            statusChipColor = androidx.compose.ui.graphics.Color(0xFF2E7D32)
            statusContentColor = androidx.compose.ui.graphics.Color.White
        }
        parcel.status == DeliveryStatus.SKIPPED -> {
            statusChipText = "Skipped"
            statusChipColor = MaterialTheme.colorScheme.errorContainer
            statusContentColor = MaterialTheme.colorScheme.onErrorContainer
        }
        isUnloadingCurrent -> {
            statusChipText = "Unloading"
            statusChipColor = MaterialTheme.colorScheme.tertiaryContainer
            statusContentColor = MaterialTheme.colorScheme.onTertiaryContainer
        }
        parcel.status == DeliveryStatus.IN_TRANSIT -> {
            statusChipText = "Out for delivery"
            statusChipColor = MaterialTheme.colorScheme.secondaryContainer
            statusContentColor = MaterialTheme.colorScheme.onSecondaryContainer
        }
        else -> {
            statusChipText = "Queued"
            statusChipColor = MaterialTheme.colorScheme.primaryContainer
            statusContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        when (parcel.assignedDriver.vehicleType) {
                            VehicleType.MOTORCYCLE -> MaterialTheme.colorScheme.primaryContainer
                            VehicleType.L300 -> MaterialTheme.colorScheme.secondaryContainer
                            VehicleType.TRUCK -> MaterialTheme.colorScheme.tertiaryContainer
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = parcel.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = statusChipColor,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.wrapContentWidth()
                    ) {
                        Text(
                            text = statusChipText,
                            color = statusContentColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "Customer: ${parcel.customerName} | Area: ${parcel.destinationAddress}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )

                Text(
                    text = "Price: ₱%,.2f | Driver: ${parcel.assignedDriver.name}".format(parcel.pricePhp),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun ParcelDetailView(
    parcel: ParcelItem,
    shiftState: DriverShiftState?,
    simSecondsElapsed: Int,
    trafficFactor: Double,
    onBack: () -> Unit,
    onFocusDriverOnMap: () -> Unit
) {
    val etaResult = remember(parcel.id, simSecondsElapsed, shiftState?.riderState, shiftState?.currentParcelIndex, shiftState?.routeStatus) {
        if (shiftState != null) {
            calculateParcelEta(parcel, shiftState, simSecondsElapsed, trafficFactor)
        } else null
    }

    val queue = shiftState?.parcelQueue ?: emptyList()
    val targetIndex = queue.indexOfFirst { it.id == parcel.id }
    val currentIdx = shiftState?.currentParcelIndex ?: 0
    val totalStops = queue.size
    val stopNum = if (targetIndex >= 0) targetIndex + 1 else 0
    val stopsBefore = (targetIndex - currentIdx).coerceAtLeast(0)

    val activeStep = when {
        parcel.status == DeliveryStatus.DELIVERED -> 4
        stopsBefore <= 3 && (shiftState?.riderState == RiderState.EN_ROUTE || shiftState?.riderState == RiderState.UNLOADING) -> 3
        shiftState?.riderState == RiderState.EN_ROUTE || shiftState?.riderState == RiderState.UNLOADING -> 2
        else -> 1
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column {
                Text(
                    text = "Track My Parcel",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = describePosition(parcel),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 1. Headline ETA / Status Banner Card
        if (parcel.status == DeliveryStatus.DELIVERED) {
            Surface(
                color = androidx.compose.ui.graphics.Color(0xFF2E7D32),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "✅ Parcel Delivered",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = androidx.compose.ui.graphics.Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Delivered successfully at customer address",
                        fontSize = 13.sp,
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f)
                    )
                }
            }
        } else if (parcel.status == DeliveryStatus.SKIPPED) {
            val skipReason = shiftState?.skippedReasons?.firstOrNull { it.contains(parcel.name) } ?: "Unreachable destination route"
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "⚠️ Delivery Skipped",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Reason: $skipReason",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        } else if (etaResult != null) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Arriving around ${etaResult.formattedArrivalTime}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${etaResult.formattedRelativeMin} (Estimated)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                }
            }
        } else {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Calculating...",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Estimating delivery route and traffic...",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 2. Queue Position Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Queue Position: Stop $stopNum of $totalStops",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (stopsBefore == 0 && parcel.status != DeliveryStatus.DELIVERED) "Your parcel is next in queue!"
                        else "$stopsBefore stops before yours",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 3. 4-Step Timeline Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Delivery Status Timeline",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                val steps = listOf("At hub", "Out for delivery", "Nearby", "Delivered")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    steps.forEachIndexed { index, title ->
                        val stepNum = index + 1
                        val isDoneOrActive = activeStep >= stepNum
                        val isCurrent = activeStep == stepNum

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isCurrent) MaterialTheme.colorScheme.primary
                                        else if (isDoneOrActive) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$stepNum",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCurrent) MaterialTheme.colorScheme.onPrimary
                                    else if (isDoneOrActive) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = title,
                                fontSize = 10.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 4. Rider Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Person, contentDescription = "Rider")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Assigned Delivery Rider",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = "Rider: ${parcel.assignedDriver.name}", fontSize = 13.sp)
                Text(text = "Vehicle: ${parcel.assignedDriver.vehicleName} (${parcel.assignedDriver.vehicleType.displayName})", fontSize = 13.sp)
                if (shiftState != null) {
                    Text(text = "Vehicle Payload: %.1f kg / %.1f kg".format(shiftState.currentPayloadWeightKg, parcel.assignedDriver.maxWeightCapacityKg), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = describePosition(shiftState),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onFocusDriverOnMap,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(Icons.Default.LocationOn, contentDescription = "Track on map")
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Track on Map",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
    }
}

const val SHOW_BOUNDARY_CIRCLE = true

@Composable
fun MapLibreView(
    modifier: Modifier = Modifier,
    driverShiftStates: List<DriverShiftState>,
    selectedDriverId: String,
    simSecondsElapsed: Int,
    runId: Long
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val pickupCenterLocation = remember { LatLng(14.0657415, 120.6334763) }
    val coverageRadiusKm = 20.0

    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var routePolyline by remember { mutableStateOf<Polyline?>(null) }
    var boundaryPolyline by remember { mutableStateOf<Polyline?>(null) }
    var targetDestMarker by remember { mutableStateOf<Marker?>(null) }
    val vehicleMarkers = remember { mutableStateMapOf<String, Marker>() }
    val activePolylines = remember { mutableStateListOf<Polyline>() }

    // Memory Tracker & LRU Cache for Callout Bitmaps
    val calloutBitmapCache = remember { LruCache<String, Bitmap>(30) }
    val lastCalloutTextMap = remember { mutableMapOf<String, String>() }
    val lastCamPos = remember { mutableStateOf<LatLng?>(null) }

    LaunchedEffect(runId, driverShiftStates) {
        routePolyline?.remove()
        routePolyline = null
        targetDestMarker?.remove()
        targetDestMarker = null
        activePolylines.forEach { it.remove() }
        activePolylines.clear()
        vehicleMarkers.values.forEach { it.remove() }
        vehicleMarkers.clear()
        calloutBitmapCache.evictAll()
    }

    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
            getMapAsync { mapboxMap ->
                mapInstance = mapboxMap
                mapboxMap.setStyle("https://tiles.openfreemap.org/styles/liberty") {
                    mapboxMap.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(pickupCenterLocation, 10.2),
                        2000
                    )

                    // 1. Draw 20km Outer Line Barrier ONLY if SHOW_BOUNDARY_CIRCLE is true (Thin, Semi-transparent)
                    if (SHOW_BOUNDARY_CIRCLE) {
                        val circlePoints = createGeodesicCircle(pickupCenterLocation, coverageRadiusKm)
                        val polylineOptions = PolylineOptions()
                            .addAll(circlePoints)
                            .color(Color.parseColor("#4D1E88E5")) // Semi-transparent blue (~30% opacity)
                            .width(1.5f) // Thin
                        boundaryPolyline = mapboxMap.addPolyline(polylineOptions)
                    }

                    // 2. Add Red Pin Marker for YTO Express Nasugbu Hub
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

    // Selected Driver's active leg parcel ID or return leg
    val activeShift = driverShiftStates.firstOrNull { it.driver.id == selectedDriverId }
    val currentSelectedParcelId = activeShift?.currentParcel?.id ?: "RETURN_HUB"

    // 1. Active Leg Route Line & Target Destination Marker Updates
    LaunchedEffect(selectedDriverId, currentSelectedParcelId, activeShift?.isReturningToHub, activeShift?.routeStatus) {
        routePolyline?.remove()
        routePolyline = null
        targetDestMarker?.remove()
        targetDestMarker = null

        val map = mapInstance ?: return@LaunchedEffect

        val shift = activeShift ?: return@LaunchedEffect
        val parcel = shift.currentParcel
        val legName = if (parcel != null) "Stop #${shift.currentParcelIndex + 1} (${parcel.name})" else "Return to Hub"

        // Item 2: Log MapLibre reported polylines size before cleanup
        val totalPolylinesBefore = map.polylines.size
        Log.d("RouteLogger", "[RouteLogger] MapLibre polylines.size before cleanup: $totalPolylinesBefore | Rider: ${shift.driver.name} | Leg: $legName")

        // Item 2: Remove every polyline from map.polylines except boundaryPolyline
        var removedCount = 0
        map.polylines.toList().forEach { poly ->
            if (poly != boundaryPolyline) {
                map.removePolyline(poly)
                removedCount++
            }
        }
        activePolylines.clear()
        Log.d("RouteLogger", "[RouteLogger] STRAY POLYLINES REMOVED: $removedCount | Rider: ${shift.driver.name} | Leg: $legName")

        // Restore destination marker whenever rider has a current parcel
        if (parcel != null) {
            val targetPoint = LatLng(parcel.destinationLat, parcel.destinationLng)
            targetDestMarker = map.addMarker(
                MarkerOptions()
                    .position(targetPoint)
                    .title("Target Stop #${shift.currentParcelIndex + 1}: ${parcel.customerName}")
                    .snippet("${parcel.name} (${parcel.weightKg} kg)")
            )
        }

        if (shift.routeStatus != RouteFetchStatus.READY || shift.currentRoute == null) {
            return@LaunchedEffect
        }

        if (parcel == null && !shift.isReturningToHub) {
            return@LaunchedEffect
        }

        val routePoints = shift.currentRoute!!.points
        if (routePoints.isNotEmpty()) {
            val expectedEnd = if (parcel != null) LatLng(parcel.destinationLat, parcel.destinationLng) else pickupCenterLocation
            val startDistMeters = calculateDistanceKm(routePoints.first().latitude, routePoints.first().longitude, shift.legStartLat, shift.legStartLng) * 1000.0
            val endDistMeters = calculateDistanceKm(routePoints.last().latitude, routePoints.last().longitude, expectedEnd.latitude, expectedEnd.longitude) * 1000.0

            val isMismatch = startDistMeters > 400.0 || endDistMeters > 400.0
            if (isMismatch) {
                Log.w("RouteLogger", "[RouteLogger] WARNING: Route mismatch for Rider: ${shift.driver.name} | Leg: $legName | Start distance (%.1fm) or End distance (%.1fm) exceeds 400m threshold.".format(startDistMeters, endDistMeters))
            }

            val lineColorHex = if (isMismatch) "#FF9800" else "#1976D2"
            val routeOptions = PolylineOptions()
                .addAll(routePoints)
                .color(Color.parseColor(lineColorHex))
                .width(3.0f)
            
            val newPoly = map.addPolyline(routeOptions)
            if (newPoly != null) {
                activePolylines.add(newPoly)
                routePolyline = newPoly
            }

            // Item 3: Log rider, leg, point count, longest segment index & endpoints (and 5 context points if >500m)
            var maxSegDistMeters = 0.0
            var maxSegIdx = 0
            for (i in 0 until routePoints.size - 1) {
                val p1 = routePoints[i]
                val p2 = routePoints[i + 1]
                val segDist = calculateDistanceKm(p1.latitude, p1.longitude, p2.latitude, p2.longitude) * 1000.0
                if (segDist > maxSegDistMeters) {
                    maxSegDistMeters = segDist
                    maxSegIdx = i
                }
            }

            val pStart = routePoints[maxSegIdx]
            val pEnd = routePoints[maxSegIdx + 1]
            Log.d("RouteLogger", "[RouteLogger] Rider: ${shift.driver.name} | Leg: $legName | Points: ${routePoints.size} | Longest Seg Index: $maxSegIdx | Length: %.1fm | Endpoint 1: (${pStart.latitude}, ${pStart.longitude}) | Endpoint 2: (${pEnd.latitude}, ${pEnd.longitude})".format(maxSegDistMeters))

            if (maxSegDistMeters > 500.0) {
                val ctxStart = (maxSegIdx - 5).coerceAtLeast(0)
                val ctxEnd = (maxSegIdx + 6).coerceAtMost(routePoints.size)
                val ctxPtsStr = routePoints.subList(ctxStart, ctxEnd).joinToString(separator = "; ") { "(${it.latitude}, ${it.longitude})" }
                Log.w("RouteLogger", "[RouteLogger] WARNING: Longest segment (%.1fm > 500m) at Index $maxSegIdx for Rider: ${shift.driver.name} | Leg: $legName | Context Points [$ctxStart..${ctxEnd - 1}]: $ctxPtsStr".format(maxSegDistMeters))
            }
        }

        // Camera focus for active driver (only if position changed and shift not completed)
        val activePos = LatLng(shift.currentLat, shift.currentLng)
        if (!shift.isShiftFullyCompleted && lastCamPos.value != activePos) {
            lastCamPos.value = activePos
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(activePos, 11.2),
                300
            )
        }
    }

    // Item 4: 2-Second Periodic Point-to-Segment Distance Checker (Local Flat Approximation)
    LaunchedEffect(selectedDriverId, runId) {
        while (true) {
            delay(2000)
            val shift = activeShift ?: continue
            val route = shift.currentRoute ?: continue
            if (shift.routeStatus != RouteFetchStatus.READY) continue

            val riderLat = shift.currentLat
            val riderLng = shift.currentLng
            val pts = route.points

            if (pts.size >= 2) {
                var minDistMeters = Double.MAX_VALUE
                var nearestSegIdx = 0

                val metersPerDegreeLat = 111139.0
                val latRad = Math.toRadians(riderLat)
                val metersPerDegreeLng = 111139.0 * kotlin.math.cos(latRad)

                for (i in 0 until pts.size - 1) {
                    val a = pts[i]
                    val b = pts[i + 1]

                    val vx = (b.longitude - a.longitude) * metersPerDegreeLng
                    val vy = (b.latitude - a.latitude) * metersPerDegreeLat
                    val wx = (riderLng - a.longitude) * metersPerDegreeLng
                    val wy = (riderLat - a.latitude) * metersPerDegreeLat

                    val vLenSq = vx * vx + vy * vy
                    val distSeg = if (vLenSq == 0.0) {
                        kotlin.math.sqrt(wx * wx + wy * wy)
                    } else {
                        val t = ((wx * vx + wy * vy) / vLenSq).coerceIn(0.0, 1.0)
                        val projX = t * vx
                        val projY = t * vy
                        val dx = wx - projX
                        val dy = wy - projY
                        kotlin.math.sqrt(dx * dx + dy * dy)
                    }

                    if (distSeg < minDistMeters) {
                        minDistMeters = distSeg
                        nearestSegIdx = i
                    }
                }

                // Log ONLY when point-to-segment distance is over 50 m to avoid flooding Logcat
                if (minDistMeters > 50.0) {
                    val pA = pts[nearestSegIdx]
                    val pB = pts[nearestSegIdx + 1]
                    Log.w("RouteLogger", "[RouteLogger] WARNING: Rider ${shift.driver.name} is %.1fm away from nearest route segment #$nearestSegIdx [(${pA.latitude}, ${pA.longitude}) -> (${pB.latitude}, ${pB.longitude})] (>50m threshold)! RiderPos: ($riderLat, $riderLng)".format(minDistMeters))
                }
            }
        }
    }

    // 2. Live Marker Position & Cached Callout Updates
    LaunchedEffect(simSecondsElapsed) {
        val map = mapInstance ?: return@LaunchedEffect

        val runtime = Runtime.getRuntime()
        val usedHeapMB = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)

        driverShiftStates.forEach { shiftState ->
            val driverId = shiftState.driver.id
            val currPoint = LatLng(shiftState.currentLat, shiftState.currentLng)
            val parcel = shiftState.currentParcel

            val calloutTitle = when {
                shiftState.routeStatus == RouteFetchStatus.LOADING -> "Loading OSRM Route..."
                shiftState.routeStatus == RouteFetchStatus.UNAVAILABLE -> "Route Unavailable"
                shiftState.isUnloading && parcel != null -> "Unloading at ${parcel.customerName}"
                shiftState.isReturningToHub -> "Returning to Nasugbu Hub"
                shiftState.isShiftFullyCompleted -> "Shift Completed"
                parcel != null -> parcel.name
                else -> "Idle"
            }
            val calloutSubtitle = when {
                shiftState.routeStatus == RouteFetchStatus.LOADING -> "Please wait"
                shiftState.routeStatus == RouteFetchStatus.UNAVAILABLE -> "Retrying..."
                shiftState.isUnloading && parcel != null -> "Handover in progress"
                shiftState.isReturningToHub -> "Hub Base"
                shiftState.isShiftFullyCompleted -> "Nasugbu Hub"
                parcel != null -> parcel.destinationAddress
                else -> "YTO Express"
            }

            val newCalloutText = "$calloutTitle|$calloutSubtitle"
            val previousText = lastCalloutTextMap[driverId]

            // ONLY regenerate or update Bitmap icon if the text content actually CHANGED!
            if (previousText != newCalloutText || !vehicleMarkers.containsKey(driverId)) {
                lastCalloutTextMap[driverId] = newCalloutText

                var cachedBitmap = calloutBitmapCache.get(newCalloutText)
                if (cachedBitmap == null || cachedBitmap.isRecycled) {
                    cachedBitmap = createVehicleCalloutMarkerBitmap(
                        vehicleType = shiftState.driver.vehicleType,
                        driverName = shiftState.driver.name,
                        parcelName = calloutTitle,
                        destAddress = calloutSubtitle
                    )
                    calloutBitmapCache.put(newCalloutText, cachedBitmap)
                    Log.d("MemoryTracker", "Created NEW Bitmap for $driverId: '$newCalloutText' | Heap Used: $usedHeapMB MB")
                }

                val vehicleIcon = IconFactory.getInstance(context).fromBitmap(cachedBitmap)
                val existingMarker = vehicleMarkers[driverId]
                if (existingMarker != null) {
                    existingMarker.position = currPoint
                    existingMarker.setIcon(vehicleIcon)
                } else {
                    val newMarker = map.addMarker(
                        MarkerOptions()
                            .position(currPoint)
                            .icon(vehicleIcon)
                            .title("Driver: ${shiftState.driver.name}")
                            .snippet("$calloutTitle -> $calloutSubtitle")
                    )
                    if (newMarker != null) {
                        vehicleMarkers[driverId] = newMarker
                    }
                }
            } else {
                // Content unchanged: simply update marker position without recreating Bitmap!
                vehicleMarkers[driverId]?.position = currPoint
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
                Lifecycle.Event.ON_DESTROY -> {
                    mapView.onDestroy()
                    calloutBitmapCache.evictAll()
                }
                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

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
            calloutBitmapCache.evictAll()
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
 * Creates a red pin marker Bitmap for the Hub.
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

    val headRadius = 26f
    val centerX = width / 2f
    val centerY = headRadius + 4f
    canvas.drawCircle(centerX, centerY, headRadius, paint)

    val path = Path().apply {
        moveTo(centerX - headRadius + 4f, centerY + 10f)
        lineTo(centerX, height.toFloat() - 4f)
        lineTo(centerX + headRadius - 4f, centerY + 10f)
        close()
    }
    canvas.drawPath(path, paint)

    paint.color = Color.WHITE
    canvas.drawCircle(centerX, centerY, 10f, paint)

    return bitmap
}

/**
 * Creates dynamic vehicle marker bitmaps with callout text box (Item & Destination) attached to the moving rider.
 */
private fun createVehicleCalloutMarkerBitmap(
    vehicleType: VehicleType,
    driverName: String,
    parcelName: String,
    destAddress: String
): Bitmap {
    val width = 260
    val height = 130
    val bitmap = createBitmap(width, height)
    val canvas = Canvas(bitmap)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Draw callout box background (Dark navy card)
    paint.color = Color.parseColor("#1E293B")
    paint.style = Paint.Style.FILL
    val rectF = RectF(4f, 4f, width - 4f, 82f)
    canvas.drawRoundRect(rectF, 14f, 14f, paint)

    // Draw callout border
    paint.color = Color.parseColor("#3B82F6")
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 3f
    canvas.drawRoundRect(rectF, 14f, 14f, paint)

    // Draw Text Line 1: Item Name
    paint.style = Paint.Style.FILL
    paint.color = Color.WHITE
    paint.textSize = 18f
    paint.typeface = Typeface.DEFAULT_BOLD
    paint.textAlign = Paint.Align.LEFT
    val itemText = "Item: $parcelName".take(24)
    canvas.drawText(itemText, 16f, 32f, paint)

    // Draw Text Line 2: Destination Address
    paint.color = Color.parseColor("#94A3B8")
    paint.textSize = 15f
    paint.typeface = Typeface.DEFAULT
    val destText = "Dest: $destAddress".take(26)
    canvas.drawText(destText, 16f, 60f, paint)

    // Draw Vehicle Circle Icon below callout
    val colorHex = when (vehicleType) {
        VehicleType.MOTORCYCLE -> "#FF6D00"
        VehicleType.L300 -> "#2979FF"
        VehicleType.TRUCK -> "#9C27B0"
    }
    paint.color = Color.parseColor(colorHex)
    paint.style = Paint.Style.FILL
    val centerX = width / 2f
    val centerY = 106f
    canvas.drawCircle(centerX, centerY, 20f, paint)

    paint.color = Color.WHITE
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 3f
    canvas.drawCircle(centerX, centerY, 18f, paint)

    paint.style = Paint.Style.FILL
    paint.textSize = 13f
    paint.textAlign = Paint.Align.CENTER
    paint.typeface = Typeface.DEFAULT_BOLD
    val symbolStr = when (vehicleType) {
        VehicleType.MOTORCYCLE -> "MC"
        VehicleType.L300 -> "VAN"
        VehicleType.TRUCK -> "TRK"
    }
    val yPos = centerY - ((paint.descent() + paint.ascent()) / 2)
    canvas.drawText(symbolStr, centerX, yPos, paint)

    return bitmap
}