package com.example.weight_based_delivery_system.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class VehicleType(
    val displayName: String,
    val maxCapacityKg: Double,
    val avgSpeedKmH: Double
) {
    MOTORCYCLE("Express Motorcycle", 15.0, 30.0),
    L300("L300 Commercial Van", 800.0, 25.0),
    TRUCK("Heavy Cargo Truck", 3000.0, 20.0)
}

enum class DeliveryStatus {
    PENDING,
    IN_TRANSIT,
    DELIVERED,
    SKIPPED
}

enum class RiderState {
    AT_HUB,
    EN_ROUTE,
    UNLOADING,
    RETURNING,
    SHIFT_COMPLETED
}

/**
 * Abstract base class for all identifiable entities in the delivery system.
 */
abstract class BaseEntity(
    open val id: String
) {
    /**
     * Returns a human-readable summary description of the domain entity.
     */
    abstract fun getSummary(): String
}

/**
 * Interface contract for trackable entities reporting GPS position and location labels.
 */
interface Trackable {
    val currentLat: Double
    val currentLng: Double
    fun getLocationLabel(): String
}

/**
 * Helper function accepting any Trackable entity polymorphically.
 */
fun describePosition(item: Trackable): String {
    val prefix = if (item is DriverShiftState) "Rider location" else "Position"
    return "$prefix: ${item.getLocationLabel()}"
}

data class DeliveryDriver(
    override val id: String,
    val name: String,
    val age: Int,
    val phone: String,
    val licensePlate: String,
    val vehicleType: VehicleType,
    val vehicleName: String,
    val maxWeightCapacityKg: Double
) : BaseEntity(id) {
    override fun getSummary(): String = "Driver $id: $name ($vehicleName)"
}

data class ParcelItem(
    override val id: String,
    val name: String,
    val brand: String,
    val category: String,
    val weightKg: Double,
    val pricePhp: Double = 0.0,
    val customerName: String,
    val destinationAddress: String,
    val destinationLat: Double,
    val destinationLng: Double,
    val assignedDriver: DeliveryDriver,
    private val initialStatus: DeliveryStatus = DeliveryStatus.PENDING,
    override var currentLat: Double = 14.0657415,
    override var currentLng: Double = 120.6334763,
    var animationProgress: Float = 0.0f
) : BaseEntity(id), Trackable {
    override fun getSummary(): String = "Parcel $id: $name ($weightKg kg) for $customerName"
    override fun getLocationLabel(): String = String.format(java.util.Locale.US, "Parcel %s at %.4f, %.4f", id, currentLat, currentLng)
    var status: DeliveryStatus by mutableStateOf(initialStatus)
    val isWithin20KmCoverage: Boolean
        get() = distanceFromHubKm <= 20.0

    val distanceFromHubKm: Double
        get() = calculateDistanceKm(14.0657415, 120.6334763, destinationLat, destinationLng)

    val remainingDistanceKm: Double
        get() = calculateDistanceKm(currentLat, currentLng, destinationLat, destinationLng)

    val estimatedTimeMinutes: Int
        get() {
            val remainingKm = remainingDistanceKm
            val speed = assignedDriver.vehicleType.avgSpeedKmH
            return kotlin.math.max(1, ((remainingKm / speed) * 60).toInt())
        }

    /**
     * Calculates realistic unloading & handover delay in simulated seconds based on parcel weight.
     */
    val unloadingDurationSeconds: Double
        get() = when {
            weightKg <= 15.0 -> 90.0    // 1.5 mins (1-2 mins) for Light parcels
            weightKg <= 800.0 -> 360.0   // 6 mins (5-7 mins) for Medium parcels
            else -> 540.0               // 9 mins (8-10 mins) for Heavy parcels
        }
}

data class DriverShiftState(
    val driver: DeliveryDriver,
    val parcelQueue: List<ParcelItem>,
    var currentParcelIndex: Int = 0,
    var legStartLat: Double = 14.0657415,
    var legStartLng: Double = 120.6334763,
    var legSecondsElapsed: Double = 0.0,
    override var currentLat: Double = 14.0657415,
    override var currentLng: Double = 120.6334763,
    var unloadingSecondsRemaining: Double = 0.0,
    var riderState: RiderState = RiderState.AT_HUB,
    var currentRoute: RoadRoute? = null,
    var routeStatus: RouteFetchStatus = RouteFetchStatus.LOADING,
    var routeSource: String = "None",
    var retryCount: Int = 0,
    val skippedReasons: SnapshotStateList<String> = mutableStateListOf()
) : Trackable {
    override fun getLocationLabel(): String = String.format(java.util.Locale.US, "%s at %.4f, %.4f", driver.name, currentLat, currentLng)
    val currentParcel: ParcelItem?
        get() = parcelQueue.getOrNull(currentParcelIndex)

    val deliveredCount: Int
        get() = parcelQueue.count { it.status == DeliveryStatus.DELIVERED }

    val skippedCount: Int
        get() = parcelQueue.count { it.status == DeliveryStatus.SKIPPED }

    val remainingCount: Int
        get() = parcelQueue.size - deliveredCount

    val currentPayloadWeightKg: Double
        get() = parcelQueue.filter { it.status != DeliveryStatus.DELIVERED }.sumOf { it.weightKg }

    val totalQueueWeightKg: Double
        get() = parcelQueue.sumOf { it.weightKg }

    val isUnloading: Boolean
        get() = riderState == RiderState.UNLOADING

    val isReturningToHub: Boolean
        get() = riderState == RiderState.RETURNING

    val isShiftFullyCompleted: Boolean
        get() = riderState == RiderState.SHIFT_COMPLETED

    val isShiftFinished: Boolean
        get() = isShiftFullyCompleted || currentParcelIndex >= parcelQueue.size
}

/**
 * Calculates distance in kilometers between two GPS coordinates using Haversine formula.
 */
fun calculateDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371.0088 // Earth radius in kilometers
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return r * c
}