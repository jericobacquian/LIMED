package com.example.weight_based_delivery_system

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.weight_based_delivery_system.data.DeliveryRepository
import com.example.weight_based_delivery_system.data.ParcelItem
import com.example.weight_based_delivery_system.data.VehicleType
import com.example.weight_based_delivery_system.ui.theme.Weight_based_delivery_systemTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    val scope = rememberCoroutineScope()

    // Load 500 dataset items
    val allParcels = remember { DeliveryRepository.loadData(context) }

    var isBottomSheetOpen by remember { mutableStateOf(false) }
    var selectedParcel by remember { mutableStateOf<ParcelItem?>(null) }
    var isSimulatingDelivery by remember { mutableStateOf(false) }
    var simulationProgress by remember { mutableFloatStateOf(0f) }

    // Floating Live Tracking Card state
    val hubLat = 14.0657415
    val hubLng = 120.6334763

    val currentSimLat = remember(selectedParcel, simulationProgress) {
        selectedParcel?.let {
            hubLat + (it.destinationLat - hubLat) * simulationProgress
        } ?: hubLat
    }

    val currentSimLng = remember(selectedParcel, simulationProgress) {
        selectedParcel?.let {
            hubLng + (it.destinationLng - hubLng) * simulationProgress
        } ?: hubLng
    }

    // Live Delivery Simulation Loop
    LaunchedEffect(isSimulatingDelivery, selectedParcel) {
        if (isSimulatingDelivery && selectedParcel != null) {
            simulationProgress = 0f
            while (simulationProgress < 1.0f) {
                delay(80)
                simulationProgress += 0.015f
                if (simulationProgress >= 1.0f) {
                    simulationProgress = 1.0f
                    break
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Background Map
        MapLibreView(
            modifier = Modifier.fillMaxSize(),
            selectedParcel = selectedParcel,
            simulatedLat = currentSimLat,
            simulatedLng = currentSimLng,
            isSimulating = isSimulatingDelivery
        )

        // Top Branch Info Overlay
        if (!isSimulatingDelivery) {
            BranchInfoCard(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp),
                branchName = "YTO Express Nasugbu Branch",
                address = "300 J. P. Laurel St, Nasugbu, Batangas",
                coordinates = "14.0657415, 120.6334763",
                coverageRadiusKm = 20
            )
        } else {
            // Live Simulation Top Status Bar
            selectedParcel?.let { parcel ->
                LiveSimulationCard(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp),
                    parcel = parcel,
                    progress = simulationProgress,
                    currentLat = currentSimLat,
                    currentLng = currentSimLng,
                    onStopSimulation = {
                        isSimulatingDelivery = false
                        simulationProgress = 0f
                    }
                )
            }
        }

        // Floating Action Button to open Parcel Tracker
        if (!isSimulatingDelivery) {
            ExtendedFloatingActionButton(
                onClick = { isBottomSheetOpen = true },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = "Deliveries")
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Track 500 Parcels (${allParcels.size})",
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Modal Bottom Sheet for Parcel List & Parcel Details
        if (isBottomSheetOpen) {
            ModalBottomSheet(
                onDismissRequest = { isBottomSheetOpen = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                ParcelTrackerSheetContent(
                    parcels = allParcels,
                    selectedParcel = selectedParcel,
                    onSelectParcel = { parcel -> selectedParcel = parcel },
                    onStartSimulation = { parcel ->
                        selectedParcel = parcel
                        isBottomSheetOpen = false
                        isSimulatingDelivery = true
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

            Spacer(modifier = Modifier.height(8.dp))

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
fun LiveSimulationCard(
    modifier: Modifier = Modifier,
    parcel: ParcelItem,
    progress: Float,
    currentLat: Double,
    currentLng: Double,
    onStopSimulation: () -> Unit
) {
    val remainingDistance = (1.0f - progress) * parcel.distanceFromHubKm
    val speed = parcel.assignedDriver.vehicleType.avgSpeedKmH
    val remainingEtaMins = kotlin.math.max(0, ((remainingDistance / speed) * 60).toInt())

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Vehicle",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (progress >= 1.0f) "DELIVERED!" else "EN ROUTE TO CUSTOMER",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 15.sp
                    )
                }

                IconButton(onClick = onStopSimulation) {
                    Icon(Icons.Default.Close, contentDescription = "Stop")
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${parcel.name} (${parcel.weightKg} kg)",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Text(
                text = "Assigned Driver: ${parcel.assignedDriver.name} (${parcel.assignedDriver.vehicleName})",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Distance: %.2f km".format(remainingDistance),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "ETA: $remainingEtaMins mins",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
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
    onStartSimulation: (ParcelItem) -> Unit,
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
                onStartSimulation = { onStartSimulation(currentDetailItem!!) }
            )
        } else {
            // List View & Filters
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Parcel Dispatch Catalog (${filteredParcels.size})",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
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

            // Vehicle Type Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedVehicleFilter == null,
                    onClick = { selectedVehicleFilter = null },
                    label = { Text("All (500)") }
                )
                FilterChip(
                    selected = selectedVehicleFilter == VehicleType.MOTORCYCLE,
                    onClick = { selectedVehicleFilter = VehicleType.MOTORCYCLE },
                    label = { Text("🏍️ Motorcycle") }
                )
                FilterChip(
                    selected = selectedVehicleFilter == VehicleType.L300,
                    onClick = { selectedVehicleFilter = VehicleType.L300 },
                    label = { Text("🚐 L300") }
                )
                FilterChip(
                    selected = selectedVehicleFilter == VehicleType.TRUCK,
                    onClick = { selectedVehicleFilter = VehicleType.TRUCK },
                    label = { Text("🚚 Truck") }
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
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = parcel.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "${parcel.weightKg} kg",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "Brand: ${parcel.brand} | Driver: ${parcel.assignedDriver.name}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        color = if (parcel.isWithin20KmCoverage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
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
    onStartSimulation: () -> Unit
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
            onClick = onStartSimulation,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Simulate")
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Simulate Delivery on Map",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
fun MapLibreView(
    modifier: Modifier = Modifier,
    selectedParcel: ParcelItem?,
    simulatedLat: Double,
    simulatedLng: Double,
    isSimulating: Boolean
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val pickupCenterLocation = remember { LatLng(14.0657415, 120.6334763) }
    val coverageRadiusKm = 20.0

    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var routePolyline by remember { mutableStateOf<Polyline?>(null) }
    var vehicleMarker by remember { mutableStateOf<Marker?>(null) }
    var destMarker by remember { mutableStateOf<Marker?>(null) }

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

    // Dynamic Route & Moving Vehicle Marker update during Simulation
    LaunchedEffect(selectedParcel, simulatedLat, simulatedLng, isSimulating) {
        val map = mapInstance ?: return@LaunchedEffect
        val parcel = selectedParcel ?: return@LaunchedEffect

        val destPoint = LatLng(parcel.destinationLat, parcel.destinationLng)
        val simPoint = LatLng(simulatedLat, simulatedLng)

        // Clear previous route / markers
        routePolyline?.remove()
        destMarker?.remove()
        vehicleMarker?.remove()

        if (isSimulating || selectedParcel != null) {
            // Draw Route Polyline from Hub to Customer Destination
            val routeOptions = PolylineOptions()
                .add(pickupCenterLocation, destPoint)
                .color(Color.parseColor("#43A047")) // Vibrant Green Route Line
                .width(5.0f)
            routePolyline = map.addPolyline(routeOptions)

            // Destination Marker
            destMarker = map.addMarker(
                MarkerOptions()
                    .position(destPoint)
                    .title("Customer: ${parcel.customerName}")
                    .snippet(parcel.destinationAddress)
            )

            // Moving Vehicle Marker
            val vehicleBitmap = createVehicleMarkerBitmap(context, parcel.assignedDriver.vehicleType)
            val vehicleIcon = IconFactory.getInstance(context).fromBitmap(vehicleBitmap)

            vehicleMarker = map.addMarker(
                MarkerOptions()
                    .position(simPoint)
                    .icon(vehicleIcon)
                    .title("Driver: ${parcel.assignedDriver.name}")
                    .snippet("En Route (${parcel.assignedDriver.vehicleName})")
            )

            // Focus map camera on vehicle & route
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(simPoint, 11.5),
                500
            )
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
private fun createVehicleMarkerBitmap(context: Context, vehicleType: VehicleType): Bitmap {
    val width = 80
    val height = 80
    val bitmap = createBitmap(width, height)
    val canvas = Canvas(bitmap)

    val colorHex = when (vehicleType) {
        VehicleType.MOTORCYCLE -> "#FF6D00" // Orange for Motorcycle
        VehicleType.L300 -> "#2979FF"       // Blue for L300
        VehicleType.TRUCK -> "#6A1B9A"      // Purple for Heavy Truck
    }

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor(colorHex)
        style = Paint.Style.FILL
    }

    // Outer Circle
    canvas.drawCircle(width / 2f, height / 2f, 36f, paint)

    // Inner White Ring
    paint.color = Color.WHITE
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 4f
    canvas.drawCircle(width / 2f, height / 2f, 34f, paint)

    return bitmap
}