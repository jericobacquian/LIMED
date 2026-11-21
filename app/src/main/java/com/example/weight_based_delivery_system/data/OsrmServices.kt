package com.example.weight_based_delivery_system.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.maplibre.android.geometry.LatLng
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.round

/**
 * Single configurable URL for the OSRM Public Routing API.
 */
object OsrmConfig {
    const val OSRM_BASE_URL = "https://router.project-osrm.org"
    const val CLEAR_CACHE_ON_START = true
    const val SAME_LOCATION_METERS = 150.0
}

object OsrmNearestService {
    suspend fun snapToNearest(
        lat: Double,
        lng: Double
    ): Pair<LatLng, Double>? = withContext(Dispatchers.IO) {
        try {
            OsrmRequestQueue.executeRateLimited {
                val urlString = "${OsrmConfig.OSRM_BASE_URL}/nearest/v1/driving/$lng,$lat"
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "WeightBasedDeliverySim/1.0 (student project)")
                connection.setRequestProperty("Accept", "application/json")

                try {
                    val httpCode = connection.responseCode
                    if (httpCode == HttpURLConnection.HTTP_OK) {
                        val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
                        val jsonRoot = org.json.JSONObject(jsonString)
                        val waypoints = jsonRoot.optJSONArray("waypoints")
                        if (waypoints != null && waypoints.length() > 0) {
                            val wp = waypoints.getJSONObject(0)
                            val distance = wp.optDouble("distance", 0.0)
                            val location = wp.getJSONArray("location")
                            val snappedLng = location.getDouble(0)
                            val snappedLat = location.getDouble(1)
                            return@executeRateLimited Pair(LatLng(snappedLat, snappedLng), distance)
                        }
                    }
                } finally {
                    connection.disconnect()
                }
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Extension point for OSRM profile mapping.
 * Public demo server currently uses 'driving' for all vehicle profiles.
 */
val VehicleType.osrmProfile: String
    get() = "driving"

enum class RouteFetchStatus {
    LOADING,
    READY,
    UNAVAILABLE
}

data class RoadRoute(
    val points: List<LatLng>,
    val cumulativeDistancesMeters: DoubleArray,
    val totalDistanceMeters: Double
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as RoadRoute
        return points == other.points && cumulativeDistancesMeters.contentEquals(other.cumulativeDistancesMeters) && totalDistanceMeters == other.totalDistanceMeters
    }

    override fun hashCode(): Int {
        var result = points.hashCode()
        result = 31 * result + cumulativeDistancesMeters.contentHashCode()
        result = 31 * result + totalDistanceMeters.hashCode()
        return result
    }
}

/**
 * Rounds coordinate values to 5 decimal places for near-identical cache matching.
 */
fun roundCoord5(value: Double): Double {
    return round(value * 100000.0) / 100000.0
}

/**
 * Builds a deterministic cache key for a route request based on rounded coordinates and profile.
 */
fun buildRouteCacheKey(
    startLat: Double,
    startLng: Double,
    destLat: Double,
    destLng: Double,
    profile: String
): String {
    val rStartLat = roundCoord5(startLat)
    val rStartLng = roundCoord5(startLng)
    val rDestLat = roundCoord5(destLat)
    val rDestLng = roundCoord5(destLng)
    return "$profile:${rStartLat},${rStartLng}->${rDestLat},${rDestLng}"
}

/**
 * Precomputes cumulative distances in meters for a polyline.
 */
fun precomputeRouteDistances(points: List<LatLng>): RoadRoute {
    if (points.isEmpty()) {
        return RoadRoute(emptyList(), DoubleArray(0), 0.0)
    }

    val cumulative = DoubleArray(points.size)
    cumulative[0] = 0.0
    var runningTotal = 0.0

    for (i in 0 until points.size - 1) {
        val p1 = points[i]
        val p2 = points[i + 1]
        val segDistMeters = calculateDistanceKm(p1.latitude, p1.longitude, p2.latitude, p2.longitude) * 1000.0
        runningTotal += segDistMeters
        cumulative[i + 1] = runningTotal
    }

    return RoadRoute(
        points = points,
        cumulativeDistancesMeters = cumulative,
        totalDistanceMeters = runningTotal
    )
}

/**
 * Interpolates position along a RoadRoute in O(log N) time using binary search.
 */
fun interpolateAlongRoute(route: RoadRoute, distanceTraveledMeters: Double): LatLng {
    val points = route.points
    val cumulative = route.cumulativeDistancesMeters

    if (points.isEmpty()) return LatLng(14.0657415, 120.6334763)
    if (points.size == 1 || distanceTraveledMeters <= 0.0) return points.first()
    if (distanceTraveledMeters >= route.totalDistanceMeters) return points.last()

    val searchIdx = cumulative.binarySearch(distanceTraveledMeters)
    if (searchIdx >= 0) {
        return points[searchIdx]
    }

    val insertionIdx = -searchIdx - 1
    val segmentIndex = (insertionIdx - 1).coerceIn(0, points.size - 2)

    val d1 = cumulative[segmentIndex]
    val d2 = cumulative[segmentIndex + 1]
    val segLength = d2 - d1

    val fraction = if (segLength > 0.0) ((distanceTraveledMeters - d1) / segLength).coerceIn(0.0, 1.0) else 0.0

    val p1 = points[segmentIndex]
    val p2 = points[segmentIndex + 1]

    val lat = p1.latitude + (p2.latitude - p1.latitude) * fraction
    val lng = p1.longitude + (p2.longitude - p1.longitude) * fraction

    return LatLng(lat, lng)
}

/**
 * Purpose: Parses OSRM GeoJSON response strings using org.json.JSONObject.
 * Extracts coordinates strictly from routes[0].geometry.coordinates while ignoring
 * trailing waypoints.location arrays to prevent route loop artifacts.
 */
object OsrmParser {

    /**
     * Parses OSRM GeoJSON response string into a list of LatLng points.
     * Parses routes[0].geometry.coordinates to extract strictly the route geometry.
     * OSRM GeoJSON format provides coordinates as [longitude, latitude].
     * Converts them to LatLng(latitude, longitude).
     */
    fun parseGeoJsonPoints(geoJsonString: String): List<LatLng> {
        val points = mutableListOf<LatLng>()
        try {
            val jsonRoot = org.json.JSONObject(geoJsonString)
            val routes = jsonRoot.optJSONArray("routes")
            if (routes != null && routes.length() > 0) {
                val route0 = routes.getJSONObject(0)
                val geometry = route0.optJSONObject("geometry")
                val coordinates = geometry?.optJSONArray("coordinates")
                if (coordinates != null) {
                    val coordCount = coordinates.length()
                    for (i in 0 until coordCount) {
                        val coordPair = coordinates.getJSONArray(i)
                        val lng = coordPair.getDouble(0)
                        val lat = coordPair.getDouble(1)
                        points.add(LatLng(lat, lng))
                    }

                    if (points.size != coordCount) {
                        Log.e("OsrmParser", "[OsrmParser] ERROR: Parsed point count (${points.size}) differs from geometry coordinates count ($coordCount)!")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("OsrmParser", "[OsrmParser] Error parsing OSRM GeoJSON: ${e.message}")
        }
        return points
    }
}

/**
 * Purpose: Thread-safe in-memory Route Cache Manager utilizing a ConcurrentHashMap.
 * Caches precomputed RoadRoute objects keyed by 5-decimal rounded coordinates to eliminate
 * redundant OSRM network requests for identical start/destination points.
 */
object RouteCacheManager {
    private val cache = ConcurrentHashMap<String, RoadRoute>()

    fun get(cacheKey: String): RoadRoute? = cache[cacheKey]

    fun put(cacheKey: String, route: RoadRoute) {
        cache[cacheKey] = route
    }

    fun clear() {
        cache.clear()
        Log.d("RouteCache", "Route cache cleared.")
    }
}

/**
 * Purpose: Thread-safe Rate-Limited Request Queue using a Kotlin Coroutine Mutex.
 * Serially spaces OSRM HTTP API network requests at least 1,000 ms apart to prevent
 * server-side rate limiting or HTTP 429 / 403 blocks.
 */
object OsrmRequestQueue {
    private val mutex = Mutex()
    private var lastRequestTimeMs = 0L

    suspend fun <T> executeRateLimited(block: suspend () -> T): T = mutex.withLock {
        try {
            val now = System.currentTimeMillis()
            val elapsed = now - lastRequestTimeMs
            if (elapsed < 1000) {
                delay(1000 - elapsed)
            }
            lastRequestTimeMs = System.currentTimeMillis()
            block()
        } finally {
            // Lock release guaranteed by Mutex.withLock
        }
    }
}

class DeterministicRejectionException(
    message: String,
    val straightDistMeters: Double,
    val routeLengthMeters: Double
) : Exception(message)

/**
 * Asynchronous OSRM Routing Service capturing exact failure reasons.
 */
object OsrmRoutingService {

    suspend fun fetchRoute(
        driverName: String,
        legName: String,
        startLat: Double,
        startLng: Double,
        destLat: Double,
        destLng: Double,
        vehicleType: VehicleType,
        currentLat: Double = 0.0,
        currentLng: Double = 0.0,
        runId: Long = 0L
    ): Result<RoadRoute> = withContext(Dispatchers.IO) {
        val profile = vehicleType.osrmProfile
        val cacheKey = buildRouteCacheKey(startLat, startLng, destLat, destLng, profile)
        val straightDistMeters = calculateDistanceKm(startLat, startLng, destLat, destLng) * 1000.0

        // Same-location shortcut: if straight-line distance is under SAME_LOCATION_METERS, return direct 2-point route
        if (straightDistMeters < OsrmConfig.SAME_LOCATION_METERS) {
            val directPoints = listOf(LatLng(startLat, startLng), LatLng(destLat, destLng))
            val directRoute = precomputeRouteDistances(directPoints)
            RouteCacheManager.put(cacheKey, directRoute)
            val logMsg = "[RouteLogger] SAME-LOCATION HOP | Rider: $driverName | Leg: $legName | Distance: %.1fm".format(straightDistMeters)
            Log.d("RouteLogger", logMsg)
            println(logMsg)
            return@withContext Result.success(directRoute)
        }

        // 1. Check cache first
        val cachedRoute = RouteCacheManager.get(cacheKey)
        if (cachedRoute != null) {
            val logMsg = "[RouteLogger] Rider: $driverName | Leg: $legName | Stage: Cache Hit | Source: Cache | HTTP: 200 (Cached) | Points: ${cachedRoute.points.size} | Length: ${cachedRoute.totalDistanceMeters.toInt()}m"
            Log.d("RouteLogger", logMsg)
            println(logMsg)
            return@withContext Result.success(cachedRoute)
        }

        // 2. Stage: Enqueued
        val enqueueMsg = "[RouteLogger] Rider: $driverName | Leg: $legName | Stage: Enqueued | Waiting in 1000ms Mutex Queue..."
            Log.d("RouteLogger", enqueueMsg)
        println(enqueueMsg)

        // 3. Execute rate-limited request with 10s connect/read timeouts
        try {
            var failReason = "Unknown routing failure"
            val route = OsrmRequestQueue.executeRateLimited {
                val sentMsg = "[RouteLogger] Rider: $driverName | Leg: $legName | Stage: Request Sent | URL: ${OsrmConfig.OSRM_BASE_URL}/route/v1/$profile/$startLng,$startLat;$destLng,$destLat"
                Log.d("RouteLogger", sentMsg)
                println(sentMsg)

                val urlString = "${OsrmConfig.OSRM_BASE_URL}/route/v1/$profile/" +
                        "$startLng,$startLat;" +
                        "$destLng,$destLat" +
                        "?overview=full&geometries=geojson"

                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 10000 // 10s connect timeout
                connection.readTimeout = 10000    // 10s read timeout
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "WeightBasedDeliverySim/1.0 (student project)")
                connection.setRequestProperty("Accept", "application/json")

                try {
                    val httpCode = connection.responseCode
                    Log.d("RouteLogger", "[RouteLogger] Rider: $driverName | Leg: $legName | Stage: HTTP Status | Code: $httpCode")

                    if (httpCode == HttpURLConnection.HTTP_OK) {
                        val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
                        val jsonRoot = org.json.JSONObject(jsonString)
                        val code = jsonRoot.optString("code", "Unknown")

                        if (code != "Ok") {
                            failReason = "OSRM Code: $code"
                            val failLog = "[RouteLogger] FAILED Route | Rider: $driverName | Leg: $legName | RunId: $runId | RequestStart: ($startLat, $startLng) | RequestEnd: ($destLat, $destLng) | StraightDist: ${straightDistMeters.toInt()}m | RiderCurrent: ($currentLat, $currentLng) | Reason: $failReason"
                            Log.e("RouteLogger", failLog)
                            return@executeRateLimited null
                        }

                        val points = OsrmParser.parseGeoJsonPoints(jsonString)
                        Log.d("RouteLogger", "[RouteLogger] Rider: $driverName | Leg: $legName | Stage: Points Parsed | Count: ${points.size}")

                        if (straightDistMeters > 2000.0 && points.size < 10) {
                            failReason = "REJECTED: Sparse Points (${points.size} < 10 for ${straightDistMeters.toInt()}m)"
                            val failLog = "[RouteLogger] REJECTED Route | Rider: $driverName | Leg: $legName | RunId: $runId | RequestStart: ($startLat, $startLng) | RequestEnd: ($destLat, $destLng) | StraightDist: ${straightDistMeters.toInt()}m | RiderCurrent: ($currentLat, $currentLng) | Reason: $failReason"
                            Log.e("RouteLogger", failLog)
                            throw DeterministicRejectionException(failReason, straightDistMeters, 0.0)
                        }

                        val roadRoute = precomputeRouteDistances(points)
                        val ratio = if (straightDistMeters > 0.1) roadRoute.totalDistanceMeters / straightDistMeters else 1.0

                        if (ratio > 3.0) {
                            val warn = "[RouteLogger] WARNING (High Ratio >3x): Rider: $driverName | Leg: $legName | Ratio: %.2fx | Straight: %.1fm | Road: %.1fm".format(ratio, straightDistMeters, roadRoute.totalDistanceMeters)
                            Log.w("RouteLogger", warn)
                            println(warn)
                        }

                        if (ratio > 10.0 && roadRoute.totalDistanceMeters > 5000.0) {
                            failReason = "REJECTED: Ratio %.2fx (>10x and >5km)".format(ratio)
                            val failLog = "[RouteLogger] REJECTED Route | Rider: $driverName | Leg: $legName | RunId: $runId | RequestStart: ($startLat, $startLng) | RequestEnd: ($destLat, $destLng) | StraightDist: ${straightDistMeters.toInt()}m | RouteLen: ${roadRoute.totalDistanceMeters.toInt()}m | RiderCurrent: ($currentLat, $currentLng) | Reason: $failReason"
                            Log.e("RouteLogger", failLog)
                            throw DeterministicRejectionException(failReason, straightDistMeters, roadRoute.totalDistanceMeters)
                        }

                        Log.d("RouteLogger", "[RouteLogger] ACCEPTED: Rider: $driverName | Leg: $legName | Points: ${points.size} | Length: ${roadRoute.totalDistanceMeters.toInt()}m | Ratio: %.2fx".format(ratio))
                        roadRoute
                    } else {
                        val errorBody = try {
                            connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                        } catch (_: Exception) {
                            ""
                        }
                        val truncatedError = if (errorBody.isNotBlank()) {
                            errorBody.take(200).replace("\n", " ")
                        } else {
                            connection.responseMessage ?: "Error"
                        }
                        failReason = "HTTP $httpCode ($truncatedError)"
                        val failLog = "[RouteLogger] FAILED Route | Rider: $driverName | Leg: $legName | RunId: $runId | RequestStart: ($startLat, $startLng) | RequestEnd: ($destLat, $destLng) | StraightDist: ${straightDistMeters.toInt()}m | RiderCurrent: ($currentLat, $currentLng) | Reason: $failReason"
                        Log.e("RouteLogger", failLog)
                        null
                    }
                } finally {
                    connection.disconnect()
                }
            }
            if (route != null) {
                RouteCacheManager.put(cacheKey, route)
                return@withContext Result.success(route)
            } else {
                return@withContext Result.failure(Exception(failReason))
            }
        } catch (e: DeterministicRejectionException) {
            Log.e("RouteLogger", "[RouteLogger] Rider: $driverName | Leg: $legName | Deterministic Rejection: ${e.message}")
            return@withContext Result.failure(e)
        } catch (e: Exception) {
            val exReason = "${e.javaClass.name}: ${e.message ?: "Unknown error"}"
            Log.e("RouteLogger", "[RouteLogger] Rider: $driverName | Leg: $legName | Failure: $exReason")
            return@withContext Result.failure(Exception(exReason))
        }
    }
}