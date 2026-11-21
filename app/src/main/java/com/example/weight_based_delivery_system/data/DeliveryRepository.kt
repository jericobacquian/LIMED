package com.example.weight_based_delivery_system.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.maplibre.android.geometry.LatLng
import kotlin.random.Random

const val MAX_COVERAGE_KM = 20.0
const val MIN_PARCELS_PER_RIDER = 40
const val MAX_PARCELS_PER_RIDER = 70

data class CustomerLocation(
    val lat: Double,
    val lng: Double,
    val area: String
)

object DeliveryRepository {

    var DEMO_SEED: Long? = null

    private var cachedDrivers: List<DeliveryDriver> = emptyList()
    private var masterCatalogPool: List<ParcelItem> = emptyList()

    fun loadMasterPool(context: Context, runSeed: Long? = DEMO_SEED): List<ParcelItem> {
        val effectiveSeed = runSeed ?: DEMO_SEED
        if (masterCatalogPool.isNotEmpty() && effectiveSeed != null) return masterCatalogPool

        masterCatalogPool = emptyList()

        // 1. Load Drivers from assets JSON
        val jsonString = context.assets.open("delivery_data.json").bufferedReader().use { it.readText() }
        val jsonObject = JSONObject(jsonString)
        val driversArray = jsonObject.getJSONArray("drivers")

        val drivers = mutableListOf<DeliveryDriver>()
        for (i in 0 until driversArray.length()) {
            val obj = driversArray.getJSONObject(i)
            val vehicleTypeStr = obj.getString("vehicleType")
            val vType = when (vehicleTypeStr) {
                "MOTORCYCLE" -> VehicleType.MOTORCYCLE
                "L300" -> VehicleType.L300
                else -> VehicleType.TRUCK
            }

            drivers.add(
                DeliveryDriver(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    age = obj.getInt("age"),
                    phone = obj.getString("phone"),
                    licensePlate = obj.getString("licensePlate"),
                    vehicleType = vType,
                    vehicleName = obj.getString("vehicleName"),
                    maxWeightCapacityKg = obj.getDouble("maxWeightCapacityKg")
                )
            )
        }
        cachedDrivers = drivers

        val driver1MotoMark = drivers.first { it.id == "DRV-001" } // Mark Santos (Motorcycle 1)
        val driver2MotoLeo = drivers.first { it.id == "DRV-002" }  // Leo Cruz (Motorcycle 2)
        val driver3L300Ramon = drivers.first { it.id == "DRV-003" } // Ramon Reyes (L300 1)
        val driver4L300Viktor = drivers.first { it.id == "DRV-004" } // Viktor Ramos (L300 2)
        val driver5TruckEduardo = drivers.first { it.id == "DRV-005" } // Eduardo Garcia (Heavy Truck)

        // 2. Delivery Locations strictly <= 13km from Hub (all 100% on dry land in Nasugbu, Lian, and Tuy)
        val locations = listOf(
            Triple("Nasugbu Commercial District", 14.0685, 120.6420),
            Triple("Barangay Bilaran, Nasugbu", 14.0620, 120.6750),
            Triple("Lian Town Proper", 14.0375, 120.6550),
            Triple("Barangay Malaruhatan, Lian", 14.0450, 120.6820),
            Triple("Palico Highway Junction, Nasugbu", 14.0710, 120.7150),
            Triple("Tuy Town Center", 14.0210, 120.7320),
            Triple("Barangay Banilad, Nasugbu", 14.0780, 120.7480),
            Triple("Barangay Catandaan, Nasugbu", 14.0820, 120.6950),
            Triple("Barangay Tumalim, Nasugbu", 14.0530, 120.6620),
            Triple("Barangay Putat, Nasugbu", 14.0880, 120.6680)
        )

        // Compute and log distance from Hub for each location at startup
        locations.forEach { loc ->
            val distKm = calculateDistanceKm(14.0657415, 120.6334763, loc.second, loc.third)
            Log.d("DeliveryRepo", "Location: ${loc.first} (${loc.second}, ${loc.third}) | Distance from Hub: %.2f km".format(distKm))
            if (distKm > 13.0) {
                Log.w("DeliveryRepo", "WARNING: Location ${loc.first} (%.2f km) exceeds 13 km threshold!".format(distKm))
            }
        }

        val customerFirstNames = listOf("Maria", "Juan", "Ana", "Carlos", "Elena", "Roberto", "Grace", "Paolo", "Jasmine", "Gabriel", "Liza", "Ramon", "Kareen", "Dominic")
        val customerLastNames = listOf("Santos", "dela Cruz", "Reyes", "Mendoza", "Ramos", "Gonzales", "Mercado", "Torres", "Villanueva", "Aquino", "Bautista", "Castro")

        data class ProductCatalogItem(
            val name: String,
            val brand: String,
            val category: String,
            val weightKg: Double,
            val pricePhp: Double
        )

        // Product Catalogs
        val lightProducts = listOf(
            ProductCatalogItem("iPhone 15 Pro Max 256GB", "Apple", "Electronics", 0.22, 84990.0),
            ProductCatalogItem("Galaxy Tab S9 Ultra", "Samsung", "Electronics", 0.73, 69990.0),
            ProductCatalogItem("WH-1000XM5 Headphones", "Sony", "Audio", 0.25, 19990.0),
            ProductCatalogItem("Air Force 1 Sneakers", "Nike", "Apparel", 1.10, 5495.0),
            ProductCatalogItem("Skincare Essentials Kit", "Celeteque", "Personal Care", 0.85, 1250.0),
            ProductCatalogItem("Coffee Beans 1kg Pack", "Monarch", "Groceries", 1.00, 850.0),
            ProductCatalogItem("Mechanical Gaming Keyboard", "Keychron", "Computer Accessories", 0.95, 4500.0),
            ProductCatalogItem("Portable Power Bank 20000mAh", "Anker", "Gadgets", 0.48, 2190.0),
            ProductCatalogItem("Smart Watch Series 9", "Apple", "Wearables", 0.15, 26490.0),
            ProductCatalogItem("Electric Toothbrush Dual Kit", "Oral-B", "Personal Care", 0.60, 3290.0),
            ProductCatalogItem("DSLR Camera Lens 50mm", "Canon", "Photography", 0.38, 8990.0),
            ProductCatalogItem("Leather Document Portfolio", "Costantino", "Office Supplies", 0.70, 1850.0),
            ProductCatalogItem("Wireless Bluetooth Speaker", "JBL", "Audio", 0.96, 3790.0)
        )

        val mediumProducts = listOf(
            ProductCatalogItem("Smart LED TV 55-inch 4K", "LG", "Home Entertainment", 16.5, 28990.0),
            ProductCatalogItem("Microwave Oven 25L", "Panasonic", "Kitchen Appliances", 12.8, 6490.0),
            ProductCatalogItem("Ergonomic Mesh Office Chair", "Sihoo", "Furniture", 18.2, 7990.0),
            ProductCatalogItem("Inverter Window Aircon 1.5HP", "Carrier", "Appliances", 34.0, 24500.0),
            ProductCatalogItem("Mountain Bike 29er Alloy Frame", "Trinx", "Sports", 14.8, 12800.0),
            ProductCatalogItem("Soundbar 5.1 System Subwoofer", "Samsung", "Audio", 11.4, 14990.0),
            ProductCatalogItem("Car Battery 12V Heavy Duty", "Motolite", "Automotive", 19.5, 6850.0),
            ProductCatalogItem("Gas Stove Heavy Duty 2-Burner", "La Germania", "Kitchen Appliances", 8.5, 4200.0),
            ProductCatalogItem("Rice Cooker Commercial 10L", "Kyowa", "Kitchen Appliances", 9.2, 5600.0),
            ProductCatalogItem("Pressure Washer 1800W", "Bosch", "Hardware", 13.5, 8950.0),
            ProductCatalogItem("Floor Water Dispenser", "Eureka", "Appliances", 15.0, 9490.0),
            ProductCatalogItem("Standing Fan Metal 18-inch", "Asahi", "Appliances", 7.8, 2890.0)
        )

        val heavyProducts = listOf(
            ProductCatalogItem("Inverter Refrigerator 2-Door 15 cu.ft.", "Sharp", "Heavy Appliances", 72.0, 34990.0),
            ProductCatalogItem("Front Load Washing Machine 10.5kg", "Whirlpool", "Heavy Appliances", 74.0, 38990.0),
            ProductCatalogItem("Chest Freezer 11 cu.ft.", "Fujidenzo", "Commercial Equipment", 68.0, 22490.0),
            ProductCatalogItem("Gasoline Generator Set 7.5kW", "Honda", "Industrial Hardware", 92.0, 45000.0),
            ProductCatalogItem("Solid Wood Dining Table 6-Seater", "Mandaue Foam", "Furniture", 110.0, 28500.0),
            ProductCatalogItem("Executive L-Shape Desk Wood", "Office Pro", "Furniture", 85.0, 18900.0),
            ProductCatalogItem("Cement Bags Pack (10 Bags)", "Holcim", "Construction Hardware", 400.0, 2450.0),
            ProductCatalogItem("Steel Rebar Bundle 12mm", "SteelAsia", "Construction Hardware", 650.0, 16800.0),
            ProductCatalogItem("Industrial Diesel Generator 15kW", "Cummins", "Heavy Machinery", 1150.0, 185000.0),
            ProductCatalogItem("Heavy Duty Pallet Storage Rack", "Standard Storage", "Industrial Hardware", 220.0, 14500.0)
        )

        val fallbackLocations = listOf(
            Triple("Nasugbu Commercial District", 14.0685, 120.6420),
            Triple("Barangay Bilaran, Nasugbu", 14.0620, 120.6750),
            Triple("Lian Town Proper", 14.0375, 120.6550),
            Triple("Barangay Malaruhatan, Lian", 14.0450, 120.6820),
            Triple("Palico Highway Junction, Nasugbu", 14.0710, 120.7150),
            Triple("Tuy Town Center", 14.0210, 120.7320),
            Triple("Barangay Banilad, Nasugbu", 14.0780, 120.7480),
            Triple("Barangay Catandaan, Nasugbu", 14.0820, 120.6950),
            Triple("Barangay Tumalim, Nasugbu", 14.0530, 120.6620),
            Triple("Barangay Putat, Nasugbu", 14.0880, 120.6680)
        )

        val customerLocations = loadCustomerLocations(context)

        val items = mutableListOf<ParcelItem>()
        var itemIdCounter = 1001

        val rngJitter = if (effectiveSeed != null) Random(effectiveSeed) else Random(System.currentTimeMillis())

        // Generate 1,500 master catalog items with random location sampling per parcel
        for (i in 1..1500) {
            val custLoc = if (customerLocations.isNotEmpty()) {
                customerLocations[rngJitter.nextInt(customerLocations.size)]
            } else {
                val fallback = fallbackLocations[rngJitter.nextInt(fallbackLocations.size)]
                CustomerLocation(fallback.second, fallback.third, fallback.first)
            }
            val custName = "${customerFirstNames[rngJitter.nextInt(customerFirstNames.size)]} ${customerLastNames[rngJitter.nextInt(customerLastNames.size)]}"

            // Jitter: max 10m jitter (+/- 0.00009 degrees on both axes)
            val jitterLat = custLoc.lat + rngJitter.nextDouble(-0.00009, 0.00009)
            val jitterLng = custLoc.lng + rngJitter.nextDouble(-0.00009, 0.00009)

            // Categorize weight & assign to appropriate vehicle weight class
            val (assignedDriver, productInfo) = when {
                i % 5 == 1 -> {
                    val prod = lightProducts[rngJitter.nextInt(lightProducts.size)]
                    driver1MotoMark to prod
                }
                i % 5 == 2 -> {
                    val prod = lightProducts[rngJitter.nextInt(lightProducts.size)]
                    driver2MotoLeo to prod
                }
                i % 5 == 3 -> {
                    val prod = mediumProducts[rngJitter.nextInt(mediumProducts.size)]
                    driver3L300Ramon to prod
                }
                i % 5 == 4 -> {
                    val prod = mediumProducts[rngJitter.nextInt(mediumProducts.size)]
                    driver4L300Viktor to prod
                }
                else -> {
                    val prod = heavyProducts[rngJitter.nextInt(heavyProducts.size)]
                    driver5TruckEduardo to prod
                }
            }

            val item = ParcelItem(
                id = "PRC-$itemIdCounter",
                name = productInfo.name,
                brand = productInfo.brand,
                category = productInfo.category,
                weightKg = productInfo.weightKg,
                pricePhp = productInfo.pricePhp,
                customerName = custName,
                destinationAddress = "${custLoc.area}, Batangas",
                destinationLat = jitterLat,
                destinationLng = jitterLng,
                assignedDriver = assignedDriver
            )

            items.add(item)
            itemIdCounter++
        }

        masterCatalogPool = items
        return items
    }

    fun loadCustomerLocations(context: Context): List<CustomerLocation> {
        val list = mutableListOf<CustomerLocation>()
        var jsonString: String? = try {
            context.assets.open("customer_locations.json").bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            null
        }

        if (jsonString == null) {
            val file = java.io.File(context.filesDir, "customer_locations.json")
            if (file.exists()) {
                try {
                    jsonString = file.readText()
                } catch (_: Exception) {}
            }
        }

        if (jsonString != null && jsonString.isNotBlank()) {
            try {
                val jsonArray = org.json.JSONArray(jsonString)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    list.add(
                        CustomerLocation(
                            lat = obj.getDouble("lat"),
                            lng = obj.getDouble("lng"),
                            area = obj.getString("area")
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e("RouteLogger", "[RouteLogger] Error parsing customer_locations.json: ${e.message}")
            }
        }

        if (list.isEmpty()) {
            Log.w("RouteLogger", "[RouteLogger] WARNING: No customer_locations.json found in assets or files directory. Using near fallback locations.")
        }

        return list
    }

    suspend fun generateAndSaveCustomerLocations(context: Context): List<CustomerLocation> = withContext(Dispatchers.IO) {
        val hubLat = 14.0657415
        val hubLng = 120.6334763
        val maxAllowedDistKm = MAX_COVERAGE_KM - 1.0

        val anchors = listOf(
            Triple("Nasugbu Town Center", 14.0685, 120.6420),
            Triple("Lian Town Proper", 14.0375, 120.6550),
            Triple("Tuy Town Center", 14.0210, 120.7320),
            Triple("Barangay Bilaran", 14.0620, 120.6750),
            Triple("Barangay Banilad", 14.0780, 120.7480),
            Triple("Balayan Highway", 14.0050, 120.7350),
            Triple("Tagaytay Highway", 14.0950, 120.7750)
        )

        // 1. Snap each anchor using OSRM /nearest/v1/driving/{lng},{lat}
        val snappedAnchors = mutableListOf<Triple<String, LatLng, Double>>()
        for (anchor in anchors) {
            val result = OsrmNearestService.snapToNearest(anchor.second, anchor.third)
            if (result != null) {
                val (snappedPt, snapDistMeters) = result
                if (snapDistMeters > 100.0) {
                    Log.w("RouteLogger", "[RouteLogger] ANCHOR SKIPPED: ${anchor.first} snap distance (%.1fm) > 100m".format(snapDistMeters))
                } else {
                    snappedAnchors.add(Triple(anchor.first, snappedPt, snapDistMeters))
                    Log.d("RouteLogger", "[RouteLogger] ANCHOR SNAPPED: ${anchor.first} -> (${snappedPt.latitude}, ${snappedPt.longitude}), snap dist: %.1fm".format(snapDistMeters))
                }
            } else {
                Log.w("RouteLogger", "[RouteLogger] ANCHOR SKIPPED: ${anchor.first} snap request failed")
            }
        }

        // 2. Fetch routes (Hub -> each snapped anchor, and anchor -> next anchor)
        val routePolylines = mutableListOf<Pair<String, RoadRoute>>()
        
        for (anchor in snappedAnchors) {
            val routeResult = OsrmRoutingService.fetchRoute(
                driverName = "Generator",
                legName = "Hub -> ${anchor.first}",
                startLat = hubLat,
                startLng = hubLng,
                destLat = anchor.second.latitude,
                destLng = anchor.second.longitude,
                vehicleType = VehicleType.MOTORCYCLE
            )
            val route = routeResult.getOrNull()
            if (route != null) {
                routePolylines.add(anchor.first to route)
            }
        }

        for (i in 0 until snappedAnchors.size - 1) {
            val a1 = snappedAnchors[i]
            val a2 = snappedAnchors[i + 1]
            val routeResult = OsrmRoutingService.fetchRoute(
                driverName = "Generator",
                legName = "${a1.first} -> ${a2.first}",
                startLat = a1.second.latitude,
                startLng = a1.second.longitude,
                destLat = a2.second.latitude,
                destLng = a2.second.longitude,
                vehicleType = VehicleType.MOTORCYCLE
            )
            val route = routeResult.getOrNull()
            if (route != null) {
                routePolylines.add(a1.first to route)
            }
        }

        // 3. Walk each route polyline and keep points roughly every 300m along road
        val rawSampledPoints = mutableListOf<Pair<String, LatLng>>()
        for ((area, route) in routePolylines) {
            var stepMeters = 0.0
            while (stepMeters <= route.totalDistanceMeters) {
                val pt = interpolateAlongRoute(route, stepMeters)
                rawSampledPoints.add(area to pt)
                stepMeters += 300.0
            }
        }

        // 4. De-duplicate with a grid (at least 150m apart) & discard distance > 19km
        val acceptedLocations = mutableListOf<CustomerLocation>()
        var excludedCount = 0

        for ((area, pt) in rawSampledPoints) {
            val distFromHub = calculateDistanceKm(hubLat, hubLng, pt.latitude, pt.longitude)
            if (distFromHub > maxAllowedDistKm) {
                excludedCount++
                continue
            }

            val tooClose = acceptedLocations.any { existing ->
                calculateDistanceKm(existing.lat, existing.lng, pt.latitude, pt.longitude) * 1000.0 < 150.0
            }

            if (!tooClose) {
                acceptedLocations.add(CustomerLocation(pt.latitude, pt.longitude, area))
            }
        }

        // 5. Check if fewer than 60 points result
        if (acceptedLocations.size < 60) {
            Log.w("RouteLogger", "[RouteLogger] GENERATOR STOPPED: Only ${acceptedLocations.size} points found (< 60 points requirement). File not saved.")
            return@withContext acceptedLocations
        }

        // 6. Write output to app/src/main/assets/customer_locations.json or filesDir
        val file = java.io.File(context.filesDir, "customer_locations.json")
        val jsonArray = org.json.JSONArray()
        acceptedLocations.forEach { loc ->
            val obj = org.json.JSONObject()
            obj.put("lat", loc.lat)
            obj.put("lng", loc.lng)
            obj.put("area", loc.area)
            jsonArray.put(obj)
        }
        file.writeText(jsonArray.toString(2))

        // 7. Log summary under RouteLogger
        val areaCounts = acceptedLocations.groupingBy { it.area }.eachCount()
        val distances = acceptedLocations.map { calculateDistanceKm(hubLat, hubLng, it.lat, it.lng) }
        val minDist = if (distances.isNotEmpty()) distances.minOrNull() ?: 0.0 else 0.0
        val maxDist = if (distances.isNotEmpty()) distances.maxOrNull() ?: 0.0 else 0.0
        val avgDist = if (distances.isNotEmpty()) distances.average() else 0.0

        val band0to3 = distances.count { it in 0.0..3.0 }
        val band3to6 = distances.count { it > 3.0 && it <= 6.0 }
        val band6to10 = distances.count { it > 6.0 && it <= 10.0 }
        val band10to15 = distances.count { it > 10.0 && it <= 15.0 }
        val band15to19 = distances.count { it > 15.0 && it <= 19.0 }

        Log.d("RouteLogger", "[RouteLogger] CUSTOMER LOCATION GENERATOR COMPLETED")
        Log.d("RouteLogger", "[RouteLogger] Output File Path: ${file.absolutePath}")
        Log.d("RouteLogger", "[RouteLogger] Total Points Collected: ${acceptedLocations.size}")
        areaCounts.forEach { (area, count) ->
            Log.d("RouteLogger", "[RouteLogger] Area: '$area' -> $count points")
        }
        Log.d("RouteLogger", "[RouteLogger] Hub Distance Stats -> Min: %.2f km | Max: %.2f km | Avg: %.2f km".format(minDist, maxDist, avgDist))
        Log.d("RouteLogger", "[RouteLogger] Distance Bands -> 0-3km: $band0to3 | 3-6km: $band3to6 | 6-10km: $band6to10 | 10-15km: $band10to15 | 15-19km: $band15to19")
        Log.d("RouteLogger", "[RouteLogger] Excluded (Dist > 19km): $excludedCount points")

        acceptedLocations
    }

    /**
     * Generates a randomized daily dispatch workload (40 to 70 parcels per rider),
     * assigned to unique riders, sorted using the SHORTEST NEAREST-NEIGHBOR ROUTE ALGORITHM
     * while prioritizing heavy payloads!
     */
    fun generateDailyDispatch(context: Context, runSeed: Long? = DEMO_SEED): List<ParcelItem> {
        val effectiveSeed = runSeed ?: DEMO_SEED ?: System.currentTimeMillis()
        if (DEMO_SEED == null) {
            masterCatalogPool = emptyList()
        }
        val masterPool = loadMasterPool(context, effectiveSeed)
        val drivers = getDrivers(context)

        val rngDaily = Random(effectiveSeed)
        val dailyParcels = mutableListOf<ParcelItem>()

        Log.d("DeliverySim", "--- Daily Dispatch Sampling (Seed: $effectiveSeed) ---")

        drivers.forEach { driver ->
            val riderTargetCount = rngDaily.nextInt(MIN_PARCELS_PER_RIDER, MAX_PARCELS_PER_RIDER + 1)
            val driverMasterParcels = masterPool.filter { it.assignedDriver.id == driver.id }
            val sampledForRider = driverMasterParcels.shuffled(rngDaily).take(riderTargetCount)

            if (driverMasterParcels.size < riderTargetCount) {
                Log.w("DeliverySim", "[DeliverySim] WARNING: Rider ${driver.name} has only ${driverMasterParcels.size} parcels in master pool (requested $riderTargetCount)")
            }

            Log.d("DeliverySim", "[DeliverySim] Rider: ${driver.name} -> Target: $riderTargetCount | Sampled: ${sampledForRider.size}")

            val unassignedDriverParcels = sampledForRider.toMutableList()
            val driverRouteQueue = mutableListOf<ParcelItem>()

            // Shortest Route Nearest-Neighbor TSP Algorithm with Weight Factor
            var currentLat = 14.0657415
            var currentLng = 120.6334763

            while (unassignedDriverParcels.isNotEmpty()) {
                // Pick next closest parcel by shortest distance from current location
                val nextParcel = unassignedDriverParcels.minByOrNull { item ->
                    val distKm = calculateDistanceKm(currentLat, currentLng, item.destinationLat, item.destinationLng)
                    distKm / (1.0 + (item.weightKg / 50.0))
                }

                if (nextParcel != null) {
                    driverRouteQueue.add(nextParcel)
                    unassignedDriverParcels.remove(nextParcel)
                    currentLat = nextParcel.destinationLat
                    currentLng = nextParcel.destinationLng
                } else {
                    break
                }
            }

            dailyParcels.addAll(driverRouteQueue)
        }

        Log.d("DeliverySim", "[DeliverySim] Total Daily Dispatch Parcels Combined: ${dailyParcels.size}")
        return dailyParcels
    }

    fun getDrivers(context: Context? = null): List<DeliveryDriver> {
        if (cachedDrivers.isEmpty() && context != null) {
            loadMasterPool(context)
        }
        return cachedDrivers
    }
}