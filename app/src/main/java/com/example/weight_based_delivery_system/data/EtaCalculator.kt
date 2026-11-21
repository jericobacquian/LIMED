package com.example.weight_based_delivery_system.data

const val ROAD_DETOUR_FACTOR = 1.4

data class ParcelEtaResult(
    val formattedArrivalTime: String,
    val formattedRelativeMin: String,
    val isEstimated: Boolean = true
)

/**
 * Returns the simulation traffic factor based on elapsed simulation seconds.
 * Rush hour (07:30 AM - 09:00 AM, i.e., 5,400 to 10,800 sim seconds) applies 25% speed reduction (0.75).
 */
fun getTrafficFactor(simSecondsElapsed: Int): Double {
    return if (simSecondsElapsed in 5400..10800) 0.75 else 1.0
}

/**
 * Pure function to calculate estimated arrival time for a parcel.
 * Recomputed whenever clock or rider state changes.
 */
fun calculateParcelEta(
    targetParcel: ParcelItem,
    shiftState: DriverShiftState,
    simSecondsElapsed: Int,
    trafficFactor: Double = getTrafficFactor(simSecondsElapsed)
): ParcelEtaResult? {
    if (targetParcel.status == DeliveryStatus.DELIVERED || targetParcel.status == DeliveryStatus.SKIPPED) {
        return null
    }

    if (shiftState.riderState == RiderState.RETURNING || shiftState.riderState == RiderState.SHIFT_COMPLETED) {
        return null
    }

    val queue = shiftState.parcelQueue
    val targetIndex = queue.indexOfFirst { it.id == targetParcel.id }
    if (targetIndex < 0 || targetIndex < shiftState.currentParcelIndex) {
        return null
    }

    val currentIdx = shiftState.currentParcelIndex
    val currentParcelAtIdx = queue.getOrNull(currentIdx)
    val effectiveTrafficFactor = trafficFactor
    val effectiveSpeedKmH = shiftState.driver.vehicleType.avgSpeedKmH * effectiveTrafficFactor
    val speedMetersPerSec = (effectiveSpeedKmH * 1000.0) / 3600.0

    var totalRemSeconds = 0.0

    // 1. Current Active Leg / Current Stop Calculation
    if (shiftState.riderState == RiderState.UNLOADING) {
        // Unloading current stop: add remaining unloading seconds
        totalRemSeconds += shiftState.unloadingSecondsRemaining
    } else {
        // EN_ROUTE or AT_HUB
        if (shiftState.riderState == RiderState.EN_ROUTE && shiftState.routeStatus == RouteFetchStatus.READY && shiftState.currentRoute != null) {
            val route = shiftState.currentRoute!!
            val distTraveledMeters = shiftState.legSecondsElapsed * speedMetersPerSec
            val remDistMeters = (route.totalDistanceMeters - distTraveledMeters).coerceAtLeast(0.0)
            val legTravelSeconds = if (speedMetersPerSec > 0) remDistMeters / speedMetersPerSec else 0.0

            totalRemSeconds += legTravelSeconds
            if (currentParcelAtIdx != null) {
                totalRemSeconds += currentParcelAtIdx.unloadingDurationSeconds
            }
        } else {
            // AT_HUB or route not ready: estimate first leg via straight-line distance x ROAD_DETOUR_FACTOR
            if (currentParcelAtIdx != null) {
                val straightKm = calculateDistanceKm(shiftState.currentLat, shiftState.currentLng, currentParcelAtIdx.destinationLat, currentParcelAtIdx.destinationLng)
                val estRoadKm = straightKm * ROAD_DETOUR_FACTOR
                val travelSeconds = if (effectiveSpeedKmH > 0) (estRoadKm / effectiveSpeedKmH) * 3600.0 else 0.0
                totalRemSeconds += travelSeconds + currentParcelAtIdx.unloadingDurationSeconds
            }
        }
    }

    // 2. Later Legs Calculation (from currentIdx + 1 to targetIndex)
    var prevLat = if (currentIdx < queue.size) queue[currentIdx].destinationLat else shiftState.currentLat
    var prevLng = if (currentIdx < queue.size) queue[currentIdx].destinationLng else shiftState.currentLng

    for (i in (currentIdx + 1)..targetIndex) {
        val parcel = queue[i]
        // Skipped stops ahead of this parcel do NOT add time
        if (parcel.status == DeliveryStatus.SKIPPED) {
            continue
        }

        val straightDistKm = calculateDistanceKm(prevLat, prevLng, parcel.destinationLat, parcel.destinationLng)
        val estimatedRoadKm = straightDistKm * ROAD_DETOUR_FACTOR

        val legTravelSeconds = if (effectiveSpeedKmH > 0) (estimatedRoadKm / effectiveSpeedKmH) * 3600.0 else 0.0
        totalRemSeconds += legTravelSeconds + parcel.unloadingDurationSeconds

        prevLat = parcel.destinationLat
        prevLng = parcel.destinationLng
    }

    val arrivalSimSeconds = simSecondsElapsed + totalRemSeconds.toInt()
    val formattedTime = formatSimTimeTo12Hour(arrivalSimSeconds)
    val relativeMinutes = (totalRemSeconds / 60.0).toInt().coerceAtLeast(1)

    return ParcelEtaResult(
        formattedArrivalTime = formattedTime,
        formattedRelativeMin = "in about $relativeMinutes min",
        isEstimated = true
    )
}

fun formatSimTimeTo12Hour(simSeconds: Int): String {
    val totalMinutes = 360 + kotlin.math.round(simSeconds / 60.0).toInt()
    val totalHours = (totalMinutes / 60) % 24
    val displayMin = totalMinutes % 60
    val period = if (totalHours < 12) "AM" else "PM"
    val displayHour = when (totalHours % 12) {
        0 -> 12
        else -> totalHours % 12
    }
    return "%02d:%02d %s".format(displayHour, displayMin, period)
}
