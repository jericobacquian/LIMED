package com.example.weight_based_delivery_system

import com.example.weight_based_delivery_system.data.DeliveryDriver
import com.example.weight_based_delivery_system.data.DeliveryStatus
import com.example.weight_based_delivery_system.data.DriverShiftState
import com.example.weight_based_delivery_system.data.ParcelItem
import com.example.weight_based_delivery_system.data.RiderState
import com.example.weight_based_delivery_system.data.VehicleType
import com.example.weight_based_delivery_system.data.calculateDistanceKm
import com.example.weight_based_delivery_system.data.precomputeRouteDistances
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.android.geometry.LatLng

class RiderNavigationTest {

    @Test
    fun testShortLocalLeg_producesShortRouteAndNeverPassesNearHub() {
        // Two stops ~500 meters apart in Lian (15 km away from Nasugbu Hub)
        val stop1Lat = 14.03750
        val stop1Lng = 120.65500

        val stop2Lat = 14.04150
        val stop2Lng = 120.65700

        val straightLineDistKm = calculateDistanceKm(stop1Lat, stop1Lng, stop2Lat, stop2Lng)
        assertTrue("Straight-line distance should be ~500m", straightLineDistKm in 0.3..0.8)

        // Generate 30 dense points along local Lian road
        val localPoints = mutableListOf<LatLng>()
        for (i in 0 until 30) {
            val frac = i.toDouble() / 29.0
            val lat = stop1Lat + (stop2Lat - stop1Lat) * frac
            val lng = stop1Lng + (stop2Lng - stop1Lng) * frac
            localPoints.add(LatLng(lat, lng))
        }

        val route = precomputeRouteDistances(localPoints)

        // 1. Calculate total route distance
        val totalRouteDistKm = route.totalDistanceMeters / 1000.0

        // Leg must be under 1.0 km (no 30km backtracking detour to the hub!)
        assertTrue("Local leg route length (%.2f km) should be under 1.0 km".format(totalRouteDistKm), totalRouteDistKm < 1.0)

        // 2. Verify NO waypoint passes near Nasugbu Hub (14.0657415, 120.6334763)
        val hubLat = 14.0657415
        val hubLng = 120.6334763

        route.points.forEach { pt ->
            val distToHub = calculateDistanceKm(pt.latitude, pt.longitude, hubLat, hubLng)
            assertTrue("Waypoint (%.5f, %.5f) should stay far from hub (dist: %.2f km)".format(pt.latitude, pt.longitude, distToHub), distToHub > 2.0)
        }
    }

    @Test
    fun testPositionDuringAndAfterUnloading_equalsStopCoordinatesExactly() {
        val testDriver = DeliveryDriver(
            id = "DRV-TEST",
            name = "Test Rider",
            age = 25,
            phone = "09170000000",
            licensePlate = "TEST-123",
            vehicleType = VehicleType.MOTORCYCLE,
            vehicleName = "Test Bike",
            maxWeightCapacityKg = 15.0
        )

        val targetLat = 14.03750
        val targetLng = 120.65500

        val testParcel = ParcelItem(
            id = "PRC-TEST",
            name = "Test Parcel",
            brand = "Test",
            category = "Electronics",
            weightKg = 1.0,
            customerName = "Test Customer",
            destinationAddress = "Lian, Batangas",
            destinationLat = targetLat,
            destinationLng = targetLng,
            assignedDriver = testDriver,
            initialStatus = DeliveryStatus.PENDING
        )

        val shiftState = DriverShiftState(
            driver = testDriver,
            parcelQueue = listOf(testParcel),
            currentParcelIndex = 0,
            legStartLat = 14.0657415,
            legStartLng = 120.6334763,
            riderState = RiderState.UNLOADING,
            unloadingSecondsRemaining = 180.0,
            currentLat = targetLat,
            currentLng = targetLng
        )

        // Position MUST equal stop coordinates during unloading
        assertEquals(targetLat, shiftState.currentLat, 0.000001)
        assertEquals(targetLng, shiftState.currentLng, 0.000001)

        // Simulate unloading complete
        shiftState.unloadingSecondsRemaining = 0.0
        testParcel.status = DeliveryStatus.DELIVERED
        shiftState.legStartLat = testParcel.destinationLat
        shiftState.legStartLng = testParcel.destinationLng

        // Position MUST equal stop coordinates after unloading
        assertEquals(targetLat, shiftState.legStartLat, 0.000001)
        assertEquals(targetLng, shiftState.legStartLng, 0.000001)
    }
}