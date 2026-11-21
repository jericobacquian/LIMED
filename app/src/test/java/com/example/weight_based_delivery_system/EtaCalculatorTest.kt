package com.example.weight_based_delivery_system

import com.example.weight_based_delivery_system.data.DeliveryDriver
import com.example.weight_based_delivery_system.data.DeliveryStatus
import com.example.weight_based_delivery_system.data.DriverShiftState
import com.example.weight_based_delivery_system.data.ParcelItem
import com.example.weight_based_delivery_system.data.RiderState
import com.example.weight_based_delivery_system.data.RoadRoute
import com.example.weight_based_delivery_system.data.RouteFetchStatus
import com.example.weight_based_delivery_system.data.VehicleType
import com.example.weight_based_delivery_system.data.calculateParcelEta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.android.geometry.LatLng

class EtaCalculatorTest {

    private val driver = DeliveryDriver(
        id = "DRV-001",
        name = "Mark Santos",
        age = 28,
        phone = "0917-123-4567",
        licensePlate = "MC-1001",
        vehicleType = VehicleType.MOTORCYCLE,
        vehicleName = "Yamaha NMAX",
        maxWeightCapacityKg = 15.0
    )

    private fun createDummyParcel(id: String, lat: Double, lng: Double, status: DeliveryStatus = DeliveryStatus.PENDING): ParcelItem {
        return ParcelItem(
            id = id,
            name = "Parcel $id",
            brand = "Brand",
            category = "Electronics",
            weightKg = 1.0,
            customerName = "Customer $id",
            destinationAddress = "Address $id",
            destinationLat = lat,
            destinationLng = lng,
            assignedDriver = driver,
            initialStatus = status
        )
    }

    /**
     * Test 1: First Stop in Queue (Rider EN_ROUTE, Route READY)
     * Hand Calculation:
     * - Driver speed: Motorcycle avgSpeedKmH = 30.0 km/h (8.333 m/s)
     * - simSecondsElapsed: 0 (6:00 AM)
     * - Route READY total distance: 1000.0 meters -> Travel time = 1000 / (30000/3600) = 120.0 seconds
     * - Unloading duration (Light parcel <= 15kg): 90.0 seconds (1.5 mins)
     * - Total remaining seconds = 120.0 + 90.0 = 210.0 seconds (3.5 minutes)
     * - Arrival sim time: 0 + 210 = 210 seconds after 6:00 AM -> 06:03:30 AM -> rounds to "06:04 AM"
     * - Relative minutes: (210 / 60) = 3 minutes -> "in about 3 min"
     */
    @Test
    fun testEta_firstStopInQueue() {
        val p1 = createDummyParcel("PRC-101", 14.0685, 120.6420)
        val route = RoadRoute(
            points = listOf(LatLng(14.0657415, 120.6334763), LatLng(14.0685, 120.6420)),
            cumulativeDistancesMeters = doubleArrayOf(0.0, 1000.0),
            totalDistanceMeters = 1000.0
        )

        val shiftState = DriverShiftState(
            driver = driver,
            parcelQueue = listOf(p1),
            currentParcelIndex = 0,
            legStartLat = 14.0657415,
            legStartLng = 120.6334763,
            legSecondsElapsed = 0.0,
            riderState = RiderState.EN_ROUTE,
            currentRoute = route,
            routeStatus = RouteFetchStatus.READY
        )

        val eta = calculateParcelEta(p1, shiftState, simSecondsElapsed = 0, trafficFactor = 1.0)

        assertNotNull(eta)
        assertTrue(eta!!.isEstimated)
        assertEquals("06:04 AM", eta.formattedArrivalTime)
        assertEquals("in about 3 min", eta.formattedRelativeMin)
    }

    /**
     * Test 2: Target 3 Stops Ahead with Known Distances (Rider EN_ROUTE, Route READY for Stop 0)
     * Hand Calculation:
     * - Driver speed: Motorcycle = 30.0 km/h (8.333 m/s), trafficFactor = 1.0
     * - Stop 0 (active leg): Route distance = 1000.0m -> Travel = 120.0s + Unloading = 90.0s -> Total Leg 0 = 210.0s
     * - Stop 0 -> Stop 1: Straight dist = 1.00025 km -> Road dist = 1.00025 * 1.4 = 1.40035 km -> Travel = (1.40035/30)*3600 = 168.04s + 90.0s = 258.04s
     * - Stop 1 -> Stop 2: Straight dist = 2.00050 km -> Road dist = 2.00050 * 1.4 = 2.80070 km -> Travel = (2.80070/30)*3600 = 336.08s + 90.0s = 426.08s
     * - Stop 2 -> Stop 3 (Target P3): Straight dist = 1.00025 km -> Road dist = 1.00025 * 1.4 = 1.40035 km -> Travel = (1.40035/30)*3600 = 168.04s + 90.0s = 258.04s
     * - Total Remaining Seconds = 210.0 + 258.04 + 426.08 + 258.04 = 1152.16 seconds (19.2 minutes)
     * - Arrival sim time: 0 + 1152.16 seconds -> 06:19:12 AM -> rounds to "06:19 AM"
     * - Relative minutes: (1152 / 60) = 19 minutes -> "in about 19 min"
     */
    @Test
    fun testEta_target3StopsAhead_knownDistances() {
        val p0 = createDummyParcel("PRC-200", 14.0685, 120.6420)
        val p1 = createDummyParcel("PRC-201", 14.0775, 120.6420) // ~1.0 km north of p0
        val p2 = createDummyParcel("PRC-202", 14.0955, 120.6420) // ~2.0 km north of p1
        val p3 = createDummyParcel("PRC-203", 14.1045, 120.6420) // ~1.0 km north of p2 (Target)

        val routeForP0 = RoadRoute(
            points = listOf(LatLng(14.0657415, 120.6334763), LatLng(14.0685, 120.6420)),
            cumulativeDistancesMeters = doubleArrayOf(0.0, 1000.0),
            totalDistanceMeters = 1000.0
        )

        val shiftState = DriverShiftState(
            driver = driver,
            parcelQueue = listOf(p0, p1, p2, p3),
            currentParcelIndex = 0,
            legStartLat = 14.0657415,
            legStartLng = 120.6334763,
            riderState = RiderState.EN_ROUTE,
            currentRoute = routeForP0,
            routeStatus = RouteFetchStatus.READY
        )

        val eta = calculateParcelEta(p3, shiftState, simSecondsElapsed = 0, trafficFactor = 1.0)

        assertNotNull(eta)
        assertTrue(eta!!.isEstimated)
        assertEquals("06:19 AM", eta.formattedArrivalTime)
        assertEquals("in about 19 min", eta.formattedRelativeMin)
    }

    /**
     * Test 3: Target Behind a Skipped Stop (P0 SKIPPED, Rider at Index 1 for P1)
     * Hand Calculation:
     * - P0 is SKIPPED at index 0. Rider currentParcelIndex = 1.
     * - Target P0 returns null (already skipped).
     * - Target P1 active leg: Route distance = 1200.0m -> Travel = 1200 / (30000/3600) = 144.0s + Unloading = 90.0s = 234.0s (3.9 minutes)
     * - Arrival sim time: 0 + 234 = 234 seconds after 6:00 AM -> 06:03:54 AM -> rounds to "06:04 AM"
     * - Relative minutes: (234 / 60) = 3 minutes -> "in about 3 min"
     */
    @Test
    fun testEta_parcelBehindSkippedStop() {
        val p0 = createDummyParcel("PRC-300", 14.0685, 120.6420, DeliveryStatus.SKIPPED)
        val p1 = createDummyParcel("PRC-301", 14.0710, 120.6550, DeliveryStatus.PENDING)

        val routeForP1 = RoadRoute(
            points = listOf(LatLng(14.0657415, 120.6334763), LatLng(14.0710, 120.6550)),
            cumulativeDistancesMeters = doubleArrayOf(0.0, 1200.0),
            totalDistanceMeters = 1200.0
        )

        val shiftState = DriverShiftState(
            driver = driver,
            parcelQueue = listOf(p0, p1),
            currentParcelIndex = 1,
            legStartLat = 14.0657415,
            legStartLng = 120.6334763,
            riderState = RiderState.EN_ROUTE,
            currentRoute = routeForP1,
            routeStatus = RouteFetchStatus.READY
        )

        val etaP0 = calculateParcelEta(p0, shiftState, simSecondsElapsed = 0)
        assertNull(etaP0)

        val etaP1 = calculateParcelEta(p1, shiftState, simSecondsElapsed = 0)
        assertNotNull(etaP1)
        assertEquals("06:04 AM", etaP1!!.formattedArrivalTime)
        assertEquals("in about 3 min", etaP1.formattedRelativeMin)
    }

    /**
     * Test 4: Rider in AT_HUB State (Route not ready yet)
     * Hand Calculation:
     * - Rider riderState = AT_HUB, currentRoute = null, routeStatus = LOADING
     * - Hub (14.0657415, 120.6334763) -> P0 (14.0837415, 120.6334763): Straight distance = 2.0005 km
     * - Estimated road distance = 2.0005 * 1.4 = 2.8007 km
     * - Travel time = (2.8007 / 30.0) * 3600 = 336.08 seconds + Unloading = 90.0 seconds = 426.08 seconds (7.1 minutes)
     * - Arrival sim time: 0 + 426.08 = 426.08 seconds after 6:00 AM -> 06:07:06 AM -> rounds to "06:07 AM"
     * - Relative minutes: (426.08 / 60) = 7 minutes -> "in about 7 min"
     */
    @Test
    fun testEta_riderAtHubState() {
        val p0 = createDummyParcel("PRC-400", 14.0837415, 120.6334763)

        val shiftState = DriverShiftState(
            driver = driver,
            parcelQueue = listOf(p0),
            currentParcelIndex = 0,
            legStartLat = 14.0657415,
            legStartLng = 120.6334763,
            riderState = RiderState.AT_HUB,
            currentRoute = null,
            routeStatus = RouteFetchStatus.LOADING
        )

        val eta = calculateParcelEta(p0, shiftState, simSecondsElapsed = 0, trafficFactor = 1.0)

        assertNotNull(eta)
        assertTrue(eta!!.isEstimated)
        assertEquals("06:07 AM", eta.formattedArrivalTime)
        assertEquals("in about 7 min", eta.formattedRelativeMin)
    }

    @Test
    fun testTrackableGetLocationLabel() {
        val parcel = createDummyParcel("PRC-1001", 14.0685, 120.6420)
        parcel.currentLat = 14.0685
        parcel.currentLng = 120.6420
        assertEquals("Parcel PRC-1001 at 14.0685, 120.6420", parcel.getLocationLabel())

        val shiftState = DriverShiftState(
            driver = driver,
            parcelQueue = listOf(parcel),
            currentParcelIndex = 0,
            currentLat = 14.0657,
            currentLng = 120.6335
        )
        assertEquals("Mark Santos at 14.0657, 120.6335", shiftState.getLocationLabel())
    }
}
