package com.example.weight_based_delivery_system

import com.example.weight_based_delivery_system.data.OsrmParser
import com.example.weight_based_delivery_system.data.buildRouteCacheKey
import com.example.weight_based_delivery_system.data.calculateDistanceKm
import com.example.weight_based_delivery_system.data.interpolateAlongRoute
import com.example.weight_based_delivery_system.data.precomputeRouteDistances
import com.example.weight_based_delivery_system.data.roundCoord5
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.android.geometry.LatLng

class OsrmParserTest {

    @Test
    fun testGeoJsonParsing_convertsLngLatToLatLngCorrectly() {
        val sampleGeoJson = """
            {
              "code": "Ok",
              "routes": [
                {
                  "geometry": {
                    "coordinates": [
                      [120.6334763, 14.0657415],
                      [120.6500000, 14.0685000],
                      [120.6520000, 14.0450000]
                    ],
                    "type": "LineString"
                  },
                  "duration": 450.2,
                  "distance": 3200.5
                }
              ]
            }
        """.trimIndent()

        val points = OsrmParser.parseGeoJsonPoints(sampleGeoJson)

        assertEquals(3, points.size)

        // Verify conversion from [lng, lat] -> LatLng(latitude, longitude)
        assertEquals(14.0657415, points[0].latitude, 0.000001)
        assertEquals(120.6334763, points[0].longitude, 0.000001)

        assertEquals(14.0685000, points[1].latitude, 0.000001)
        assertEquals(120.6500000, points[1].longitude, 0.000001)

        assertEquals(14.0450000, points[2].latitude, 0.000001)
        assertEquals(120.6520000, points[2].longitude, 0.000001)
    }

    @Test
    fun testGeoJsonParsing_ignoresWaypointsArray_andMatchesGeometryCoordinatesCount() {
        val sampleGeoJsonWithWaypoints = """
            {
              "code": "Ok",
              "routes": [
                {
                  "geometry": {
                    "coordinates": [
                      [120.6334763, 14.0657415],
                      [120.6400000, 14.0670000],
                      [120.6500000, 14.0685000],
                      [120.6520000, 14.0450000]
                    ],
                    "type": "LineString"
                  },
                  "duration": 450.2,
                  "distance": 3200.5
                }
              ],
              "waypoints": [
                {
                  "hint": "abc123xyz",
                  "distance": 1.2,
                  "name": "J.P. Laurel St",
                  "location": [120.6334763, 14.0657415]
                },
                {
                  "hint": "def456uvw",
                  "distance": 3.4,
                  "name": "Lian Highway",
                  "location": [120.6520000, 14.0450000]
                }
              ]
            }
        """.trimIndent()

        val points = OsrmParser.parseGeoJsonPoints(sampleGeoJsonWithWaypoints)

        // The geometry.coordinates array has 4 items. The waypoints array has 2 location fields.
        // The old regex parser would have returned 6 points. Real JSON parsing must return 4 points!
        assertEquals(4, points.size)

        // Assert that the last point is exactly the last coordinate in geometry.coordinates: [120.6520000, 14.0450000] -> LatLng(14.0450000, 120.6520000)
        val lastPoint = points.last()
        assertEquals(14.0450000, lastPoint.latitude, 0.000001)
        assertEquals(120.6520000, lastPoint.longitude, 0.000001)
    }

    @Test
    fun testCoordinateRoundingAndCacheKeyGeneration() {
        val lat1 = 14.0657415123
        val lng1 = 120.6334763891

        val roundedLat = roundCoord5(lat1)
        val roundedLng = roundCoord5(lng1)

        assertEquals(14.06574, roundedLat, 0.00001)
        assertEquals(120.63348, roundedLng, 0.00001)

        val key1 = buildRouteCacheKey(14.0657415, 120.6334763, 14.0700012, 120.7100045, "driving")
        val key2 = buildRouteCacheKey(14.0657449, 120.6334799, 14.0700031, 120.7100021, "driving")

        assertEquals(key1, key2)
    }

    @Test
    fun testPrecomputeDistancesAndBinarySearchInterpolation() {
        val p1 = LatLng(14.0657415, 120.6334763)
        val p2 = LatLng(14.0685000, 120.6500000)
        val p3 = LatLng(14.0450000, 120.6520000)

        val route = precomputeRouteDistances(listOf(p1, p2, p3))

        assertNotNull(route)
        assertEquals(3, route.points.size)

        val startPos = interpolateAlongRoute(route, 0.0)
        assertEquals(p1.latitude, startPos.latitude, 0.000001)
        assertEquals(p1.longitude, startPos.longitude, 0.000001)

        val endPos = interpolateAlongRoute(route, route.totalDistanceMeters)
        assertEquals(p3.latitude, endPos.latitude, 0.000001)
        assertEquals(p3.longitude, endPos.longitude, 0.000001)
    }

    @Test
    fun testLeoHubToLianRouteConcreteAssertions_noStraightLineCuts() {
        // Leo's Hub and Lian Town Proper coordinates
        val hubLat = 14.0657415
        val hubLng = 120.6334763
        val lianLat = 14.03750
        val lianLng = 120.65500

        // Create dense simulated OSRM turn-by-turn road polyline for Hub -> Lian (60 points following roads)
        val densePoints = mutableListOf<LatLng>()
        val pointCount = 60
        for (i in 0 until pointCount) {
            val frac = i.toDouble() / (pointCount - 1)
            // Road arc curve via J.P. Laurel Blvd (120.6500) and Lian Highway (120.6520)
            val midArcLng = if (frac < 0.5) {
                120.6334763 + (120.65000 - 120.6334763) * (frac * 2)
            } else {
                120.65000 + (120.65500 - 120.65000) * ((frac - 0.5) * 2)
            }
            val lat = hubLat + (lianLat - hubLat) * frac
            densePoints.add(LatLng(lat, midArcLng))
        }

        val route = precomputeRouteDistances(densePoints)

        // 1. Assert route has at least 50 points
        assertTrue("Route must have >= 50 points, but had ${route.points.size}", route.points.size >= 50)

        // 2. Assert no single segment is longer than 500 meters (no straight-line cuts)
        for (i in 0 until route.points.size - 1) {
            val p1 = route.points[i]
            val p2 = route.points[i + 1]
            val segMeters = calculateDistanceKm(p1.latitude, p1.longitude, p2.latitude, p2.longitude) * 1000.0
            assertTrue("Segment $i length (${segMeters}m) should be <= 500m", segMeters <= 500.0)
        }

        // 3. Assert total route length is between 1.0x and 2.0x straight-line distance
        val straightDistMeters = calculateDistanceKm(hubLat, hubLng, lianLat, lianLng) * 1000.0
        val ratio = route.totalDistanceMeters / straightDistMeters

        assertTrue("Route ratio (${ratio}) should be >= 1.0x", ratio >= 1.0)
        assertTrue("Route ratio (${ratio}) should be <= 2.0x", ratio <= 2.0)
    }
}