package com.example.weight_based_delivery_system

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.setValue
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
import com.example.weight_based_delivery_system.data.DeliveryRepository
import com.example.weight_based_delivery_system.data.ParcelItem
import com.example.weight_based_delivery_system.data.VehicleType
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeliveryDashboard(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // Load Drivers
    val drivers = remember { DeliveryRepository.getDrivers() }

    // State for Daily Dispatch Parcels (Random 350 to 500 items, sorted Heaviest & Farthest First)
    val dailyDispatchParcels = remember { mutableStateListOf<ParcelItem>() }

    fun refreshDailyDispatch() {
        dailyDispatchParcels.clear()
        dailyDispatchParcels.addAll(DeliveryRepository.generateDailyDispatch(context))
    }

    LaunchedEffect(Unit) {
        if (dailyDispatchParcels.isEmpty()) {
            refreshDailyDispatch()
        }
    }

    // Pick 1 active parcel per driver for simultaneous dispatch at 6:00 AM
    val activeParcels = remember(dailyDispatchParcels.size) {
        mutableStateListOf<ParcelItem>().apply {
            drivers.forEach { driver ->
                val assigned = dailyDispatchParcels.firstOrNull { it.assignedDriver.id == driver.id }
                if (assigned != null) add(assigned)
            }
        }
    }

    // Selected Driver Focus State (Default to Driver 1 - Mark Santos)
    var selectedDriverId by remember { mutableStateOf(drivers.firstOrNull()?.id ?: "") }
    val activeSelectedParcel = remember(selectedDriverId, activeParcels) {
        activeParcels.firstOrNull { it.assignedDriver.id == selectedDriverId }
    }

    // Central 6:00 AM Dispatch Simulation Clock & Multiplier
    var simSecondsElapsed by remember { mutableIntStateOf(0) } // Seconds past 06:00 AM
    var simSpeedMultiplier by remember { mutableIntStateOf(30) } // 30x default fast-forward speed
    var isPaused by remember { mutableStateOf(false) }

    // UI Panel Toggles for Right Side Vertical Buttons
    var showClockPanel by remember { mutableStateOf(false) }
    var showDriverInfoPanel by remember { mutableStateOf(false) }
    var showBranchInfoPanel by remember { mutableStateOf(false) }

    var isBottomSheetOpen by remember { mutableStateOf(false) }
    var detailParcelItem by remember { mutableStateOf<ParcelItem?>(null) }

    // Central 6:00 AM Simultaneous Dispatch Engine
    LaunchedEffect(isPaused, simSpeedMultiplier, dailyDispatchParcels.size) {
        while (!isPaused) {
            delay(100)
            simSecondsElapsed += (1 * (simSpeedMultiplier / 10).coerceAtLeast(1))

            // Update live progress and coordinates for ALL 5 vehicles along road paths
            activeParcels.forEach { parcel ->
                val waypoints = parcel.getRoadWaypoints()
                val totalDistanceKm = parcel.distanceFromHubKm
                val speedKmH = parcel.assignedDriver.vehicleType.avgSpeedKmH
                val totalTravelSeconds = ((totalDistanceKm / speedKmH) * 3600).coerceAtLeast(60.0)

                val progress = (simSecondsElapsed / totalTravelSeconds).coerceIn(0.0, 1.0).toFloat()
                parcel.animationProgress = progress

                // Interpolate vehicle position along road waypoints
                val currPos = interpolatePath(waypoints, progress.toDouble())
                parcel.currentLat = currPos.first
                parcel.currentLng = currPos.second
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Lightweight Map View displaying 5 moving vehicles + ONLY selected driver's road route line (NO FLASHING)
        MapLibreView(
            modifier = Modifier.fillMaxSize(),
            activeParcels = activeParcels,
            selectedDriverId = selectedDriverId,
            simSecondsElapsed = simSecondsElapsed
        )

        // Pop-Over Panels (Only appear when user clicks the dedicated right-side buttons)
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .fillMaxWidth(0.8f)
        ) {
            AnimatedVisibility(visible = showClockPanel, enter = fadeIn(), exit = fadeOut()) {
                SimulationClockCard(
                    simSecondsElapsed = simSecondsElapsed,
                    speedMultiplier = simSpeedMultiplier,
                    isPaused = isPaused,
                    onTogglePause = { isPaused = !isPaused },
                    onSpeedChange = { newSpeed -> simSpeedMultiplier = newSpeed },
                    onClose = { showClockPanel = false }
                )
            }

            if (showClockPanel && showDriverInfoPanel) Spacer(modifier = Modifier.height(8.dp))

            AnimatedVisibility(visible = showDriverInfoPanel, enter = fadeIn(), exit = fadeOut()) {
                activeSelectedParcel?.let { parcel ->
                    ActiveDriverStatusCard(
                        parcel = parcel,
                        onClose = { showDriverInfoPanel = false }
                    )
                }
            }

            if ((showClockPanel || showDriverInfoPanel) && showBranchInfoPanel) Spacer(modifier = Modifier.height(8.dp))

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

        // RIGHT-MOST SIDE VERTICAL BUTTON BAR
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.End
        ) {
            // 0. Refresh / New Delivery Day Button
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

            // 1. Clock Panel Toggle Button
            SmallFloatingActionButton(
                onClick = {
                    showClockPanel = !showClockPanel
                    if (showClockPanel) {
                        showDriverInfoPanel = false
                        showBranchInfoPanel = false
                    }
                },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Info, contentDescription = "Clock Panel")
            }

            // 2. Active Driver Info Button
            SmallFloatingActionButton(
                onClick = {
                    showDriverInfoPanel = !showDriverInfoPanel
                    if (showDriverInfoPanel) {
                        showClockPanel = false
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
                        showClockPanel = false
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
                        text = "Today (${dailyDispatchParcels.size})",
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
fun SimulationClockCard(
    simSecondsElapsed: Int,
    speedMultiplier: Int,
    isPaused: Boolean,
    onTogglePause: () -> Unit,
    onSpeedChange: (Int) -> Unit,
    onClose: () -> Unit
) {
    val totalMins = simSecondsElapsed / 60
    val hours = 6 + (totalMins / 60)
    val mins = totalMins % 60
    val clockStr = "%02d:%02d AM".format(if (hours > 12) hours - 12 else hours, mins)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "🌅 DISPATCH SHIFT CLOCK",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = clockStr,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onTogglePause) {
                        Icon(
                            imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Close,
                            contentDescription = "Pause",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.outline)
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Speed Multiplier Chips in a horizontal scrollable row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(10, 30, 60).forEach { speed ->
                    FilterChip(
                        selected = speedMultiplier == speed,
                        onClick = { onSpeedChange(speed) },
                        label = { Text("${speed}x", fontSize = 11.sp, maxLines = 1, softWrap = false) }
                    )
                }
            }
        }
    }
}

@Composable
fun ActiveDriverStatusCard(
    parcel: ParcelItem,
    onClose: () -> Unit
) {
    val remainingKm = parcel.remainingDistanceKm
    val speed = parcel.assignedDriver.vehicleType.avgSpeedKmH
    val remainingEtaMins = kotlin.math.max(0, ((remainingKm / speed) * 60).toInt())

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
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
                    Text(
                        text = "${parcel.assignedDriver.name} (${parcel.assignedDriver.vehicleName})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.wrapContentWidth()
                ) {
                    Text(
                        text = parcel.assignedDriver.vehicleType.name,
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

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "📦 Parcel: ${parcel.name} (${parcel.weightKg} kg)",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                softWrap = false
            )

            Text(
                text = "📍 Dest: ${parcel.destinationAddress}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                maxLines = 1,
                softWrap = false
            )

            Spacer(modifier = Modifier.height(4.dp))

            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "⚖️ Priority Order: Heaviest & Farthest First",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Distance: %.2f km".format(remainingKm),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = if (parcel.animationProgress >= 1.0f) "✅ DELIVERED!" else "⏱️ ETA: $remainingEtaMins mins",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
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
                FilterChip(
                    selected = selectedDriverId == driver.id,
                    onClick = { onSelectDriver(driver.id) },
                    label = {
                        Text(
                            text = "${driver.name} (${driver.vehicleType.name})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun ParcelTrackerSheetContent(
    parcels: List<ParcelItem>,
    selectedParcel: ParcelItem?,
    onSelectParcel: (ParcelItem) -> Unit,
    onFocusDriverOnMap: (String) -> Unit,
    onClose: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedVehicleFilter by remember { mutableStateOf<VehicleType?>(null) }
    var currentDetailItem by remember { mutableStateOf(selectedParcel) }

    val filteredParcels = remember(searchQuery, selectedVehicleFilter, parcels) {
        parcels.filter { item ->
            val matchesSearch = item.name.contains(searchQuery, ignoreCase = true) ||
                    item.brand.contains(searchQuery, ignoreCase = true) ||
                    item.id.contains(searchQuery, ignoreCase = true) ||
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
            // Detailed View of Selected Parcel
            ParcelDetailView(
                parcel = currentDetailItem!!,
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
                placeholder = { Text("Search by parcel, brand, driver...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Vehicle Type Filter Chips in a horizontal scrollable row to prevent vertical wrapping
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
                    ParcelItemCard(
                        parcel = parcel,
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
    onClick: () -> Unit
) {
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
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.wrapContentWidth()
                    ) {
                        Text(
                            text = "${parcel.weightKg} kg",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "Brand: ${parcel.brand} | Driver: ${parcel.assignedDriver.name}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (parcel.isWithin20KmCoverage) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (parcel.isWithin20KmCoverage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (parcel.isWithin20KmCoverage) "Inside 20km Zone (%.1f km)".format(parcel.distanceFromHubKm)
                        else "Outside 20km Zone (%.1f km)".format(parcel.distanceFromHubKm),
                        fontSize = 11.sp,
                        color = if (parcel.isWithin20KmCoverage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
fun ParcelDetailView(
    parcel: ParcelItem,
    onBack: () -> Unit,
    onFocusDriverOnMap: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Parcel Dispatch Details",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Parcel Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = parcel.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(text = "Brand: ${parcel.brand} | Category: ${parcel.category}", fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Weight: ${parcel.weightKg} kg",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "ID: ${parcel.id}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Driver Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Person, contentDescription = "Driver")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Assigned Delivery Driver",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Driver Name: ${parcel.assignedDriver.name} (Age: ${parcel.assignedDriver.age})", fontSize = 13.sp)
                Text(text = "Vehicle: ${parcel.assignedDriver.vehicleName} (${parcel.assignedDriver.licensePlate})", fontSize = 13.sp)
                Text(text = "Contact: ${parcel.assignedDriver.phone}", fontSize = 13.sp)
                Text(text = "Vehicle Max Weight Limit: ${parcel.assignedDriver.maxWeightCapacityKg} kg", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Destination & ETA Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Place, contentDescription = "ETA")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Delivery Destination & ETA",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Customer: ${parcel.customerName}", fontSize = 13.sp)
                Text(text = "Address: ${parcel.destinationAddress}", fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Distance: %.2f km".format(parcel.distanceFromHubKm),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Est. ETA: ${parcel.estimatedTimeMinutes} mins",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
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
            Icon(Icons.Default.LocationOn, contentDescription = "Focus Driver")
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Focus Driver Route on Map",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
fun MapLibreView(
    modifier: Modifier = Modifier,
    activeParcels: List<ParcelItem>,
    selectedDriverId: String,
    simSecondsElapsed: Int
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val pickupCenterLocation = remember { LatLng(14.0657415, 120.6334763) }
    val coverageRadiusKm = 20.0

    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var routePolyline by remember { mutableStateOf<Polyline?>(null) }
    val vehicleMarkers = remember { mutableStateMapOf<String, Marker>() }

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

                    // 1. Draw 20km Outer Line Barrier ONLY
                    val circlePoints = createGeodesicCircle(pickupCenterLocation, coverageRadiusKm)
                    val polylineOptions = PolylineOptions()
                        .addAll(circlePoints)
                        .color(Color.parseColor("#1E88E5"))
                        .width(4.0f)
                    mapboxMap.addPolyline(polylineOptions)

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

    // 1. NON-FLASHING Route Polyline update (ONLY re-runs when selectedDriverId changes)
    LaunchedEffect(selectedDriverId, activeParcels.size) {
        val map = mapInstance ?: return@LaunchedEffect
        val selectedParcel = activeParcels.firstOrNull { it.assignedDriver.id == selectedDriverId } ?: return@LaunchedEffect

        // Remove previous route line and add thin, solid blue road line
        routePolyline?.remove()
        val waypoints = selectedParcel.getRoadWaypoints().map { LatLng(it.first, it.second) }
        val routeOptions = PolylineOptions()
            .addAll(waypoints)
            .color(Color.parseColor("#1976D2")) // Solid Vibrant Blue Road Line
            .width(3.0f) // Thin line width
        routePolyline = map.addPolyline(routeOptions)
    }

    // 2. Live Marker Position Updates (Updates vehicle positions on every tick without re-creating route polyline!)
    LaunchedEffect(simSecondsElapsed) {
        val map = mapInstance ?: return@LaunchedEffect

        activeParcels.forEach { parcel ->
            val driverId = parcel.assignedDriver.id
            val currPoint = LatLng(parcel.currentLat, parcel.currentLng)

            val existingMarker = vehicleMarkers[driverId]
            if (existingMarker != null) {
                existingMarker.position = currPoint
            } else {
                val vehicleBitmap = createVehicleMarkerBitmap(parcel.assignedDriver.vehicleType)
                val vehicleIcon = IconFactory.getInstance(context).fromBitmap(vehicleBitmap)
                val newMarker = map.addMarker(
                    MarkerOptions()
                        .position(currPoint)
                        .icon(vehicleIcon)
                        .title("Driver: ${parcel.assignedDriver.name}")
                        .snippet("${parcel.assignedDriver.vehicleName} (${parcel.assignedDriver.licensePlate})")
                )
                if (newMarker != null) {
                    vehicleMarkers[driverId] = newMarker
                }
            }

            // Smooth camera follow for active driver
            if (driverId == selectedDriverId) {
                map.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(currPoint, 11.2),
                    300
                )
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
 * Interpolates position along a list of waypoints based on progress (0.0 to 1.0).
 */
private fun interpolatePath(waypoints: List<Pair<Double, Double>>, progress: Double): Pair<Double, Double> {
    if (waypoints.isEmpty()) return 14.0657415 to 120.6334763
    if (waypoints.size == 1 || progress <= 0.0) return waypoints.first()
    if (progress >= 1.0) return waypoints.last()

    val totalSegments = waypoints.size - 1
    val scaledProgress = progress * totalSegments
    val segmentIndex = scaledProgress.toInt().coerceIn(0, totalSegments - 1)
    val segmentProgress = scaledProgress - segmentIndex

    val p1 = waypoints[segmentIndex]
    val p2 = waypoints[segmentIndex + 1]

    val lat = p1.first + (p2.first - p1.first) * segmentProgress
    val lng = p1.second + (p2.second - p1.second) * segmentProgress

    return lat to lng
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
 * Creates custom vehicle marker bitmaps based on vehicle type (Motorcycle, L300, Truck).
 */
private fun createVehicleMarkerBitmap(vehicleType: VehicleType): Bitmap {
    val width = 84
    val height = 84
    val bitmap = createBitmap(width, height)
    val canvas = Canvas(bitmap)

    val colorHex = when (vehicleType) {
        VehicleType.MOTORCYCLE -> "#FF6D00" // Vibrant Orange for Motorcycle 🏍️
        VehicleType.L300 -> "#2979FF"       // Vibrant Blue for L300 🚐
        VehicleType.TRUCK -> "#9C27B0"      // Vibrant Purple for Heavy Truck 🚚
    }

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor(colorHex)
        style = Paint.Style.FILL
    }

    // Outer Circle Shadow/Border
    val centerX = width / 2f
    val centerY = height / 2f
    canvas.drawCircle(centerX, centerY, 38f, paint)

    // Inner White Ring
    paint.color = Color.WHITE
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 5f
    canvas.drawCircle(centerX, centerY, 35f, paint)

    // Inner Solid Circle
    paint.style = Paint.Style.FILL
    paint.color = Color.parseColor(colorHex)
    canvas.drawCircle(centerX, centerY, 28f, paint)

    // Center Vehicle Symbol Text
    paint.color = Color.WHITE
    paint.textSize = 22f
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