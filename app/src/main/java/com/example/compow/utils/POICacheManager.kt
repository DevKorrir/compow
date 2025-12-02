package com.example.compow.utils

import android.content.Context
import android.util.Log
import com.example.compow.data.NearbyPlace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Aggressive caching system to minimize Google Places API calls
 * Caches results based on geographic grid to reduce costs to near $0
 */
class POICacheManager(private val context: Context) {

    private val mutex = Mutex()
    private val memoryCache = mutableMapOf<String, CachedResult>()

    companion object {
        private const val TAG = "POICacheManager"
        private const val CACHE_FILE_NAME = "poi_cache.json"

        // Grid size: ~1km = 0.01 degrees at equator
        private const val GRID_SIZE = 0.01

        // Cache validity: 7 days
        private const val CACHE_VALIDITY_MS = 7L * 24 * 60 * 60 * 1000

        // Maximum cache entries
        private const val MAX_CACHE_ENTRIES = 500
    }

    // Google Places API KEY - REPLACE WITH YOUR KEY
    private val API_KEY: String by lazy {
        try {
            val ai = context.packageManager.getApplicationInfo(
                context.packageName,
                android.content.pm.PackageManager.GET_META_DATA
            )
            ai.metaData.getString("com.google.android.geo.API_KEY") ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load API key: ${e.message}")
            ""
        }
    }

    data class CachedResult(
        val hospitals: List<NearbyPlace>,
        val policeStations: List<NearbyPlace>,
        val timestamp: Long,
        val gridKey: String
    )

    init {
        loadCacheFromDisk()
    }

    /**
     * Get cached results or return null if cache miss
     */
    suspend fun getCachedResults(latitude: Double, longitude: Double): CachedResult? {
        val gridKey = getGridKey(latitude, longitude)

        return mutex.withLock {
            val cached = memoryCache[gridKey]

            if (cached != null) {
                val age = System.currentTimeMillis() - cached.timestamp
                if (age < CACHE_VALIDITY_MS) {
                    Log.d(TAG, "✅ Cache HIT for grid $gridKey (age: ${age / 1000 / 60} min)")
                    return@withLock cached
                } else {
                    Log.d(TAG, "⚠️ Cache EXPIRED for grid $gridKey")
                    memoryCache.remove(gridKey)
                }
            }

            Log.d(TAG, "❌ Cache MISS for grid $gridKey")
            null
        }
    }

    /**
     * Store results in cache
     */
    suspend fun cacheResults(
        latitude: Double,
        longitude: Double,
        hospitals: List<NearbyPlace>,
        policeStations: List<NearbyPlace>
    ) {
        val gridKey = getGridKey(latitude, longitude)
        val cached = CachedResult(
            hospitals = hospitals,
            policeStations = policeStations,
            timestamp = System.currentTimeMillis(),
            gridKey = gridKey
        )

        mutex.withLock {
            // Prevent overflow
            if (memoryCache.size >= MAX_CACHE_ENTRIES) {
                val oldestKey = memoryCache.minByOrNull { it.value.timestamp }?.key
                oldestKey?.let { memoryCache.remove(it) }
            }

            memoryCache[gridKey] = cached
            Log.d(TAG, "💾 Cached results for grid $gridKey (${memoryCache.size} entries)")
        }

        saveCacheToDisk()
    }

    /**
     * Fetch POIs from Google Places API and cache them
     * This is the main method to use - it handles cache + API
     */
    suspend fun fetchAndCacheNearby(lat: Double, lng: Double): CachedResult {
        Log.d(TAG, "🔍 Fetching POIs from Google Places API...")

        val hospitals = performTextSearch(lat, lng, "hospital", "hospital").take(3)
        val police = performTextSearch(lat, lng, "police station", "police").take(3)

        Log.d(TAG, "✅ Fetched ${hospitals.size} hospitals, ${police.size} police stations")

        // Store in cache
        cacheResults(lat, lng, hospitals, police)

        return CachedResult(
            hospitals = hospitals,
            policeStations = police,
            timestamp = System.currentTimeMillis(),
            gridKey = getGridKey(lat, lng)
        )
    }

    /**
     * Perform a text search using Google Places API v1
     */
    private suspend fun performTextSearch(
        lat: Double,
        lng: Double,
        query: String,
        includedType: String
    ): List<NearbyPlace> = withContext(Dispatchers.IO) {

        val nearbyPlaces = mutableListOf<NearbyPlace>()

        try {
            if (API_KEY.isEmpty()) {
                Log.e(TAG, "❌ API_KEY is empty! Check your AndroidManifest.xml")
                return@withContext nearbyPlaces
            }

            val url = URL("https://places.googleapis.com/v1/places:searchText")

            val body = JSONObject().apply {
                put("textQuery", query)
                put("pageSize", 5)
                put("includedType", includedType)
                put("rankPreference", "DISTANCE")
                put("locationBias", JSONObject().apply {
                    put("circle", JSONObject().apply {
                        put("center", JSONObject().apply {
                            put("latitude", lat)
                            put("longitude", lng)
                        })
                        put("radius", 50000.0)
                    })
                })
            }

            Log.d(TAG, "🌐 Searching for '$query' near ($lat, $lng)")

            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("X-Goog-Api-Key", API_KEY)
            connection.setRequestProperty("X-Goog-FieldMask",
                "places.displayName,places.formattedAddress,places.location,places.id")
            connection.doOutput = true
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            connection.outputStream.use { os ->
                os.write(body.toString().toByteArray())
            }

            val responseCode = connection.responseCode
            if (responseCode != 200) {
                val errorStream = connection.errorStream?.bufferedReader()?.readText()
                Log.e(TAG, "❌ API Error $responseCode: $errorStream")
                return@withContext nearbyPlaces
            }

            val response = connection.inputStream.bufferedReader().readText()
            val json = JSONObject(response)

            val places = json.optJSONArray("places") ?: JSONArray()
            Log.d(TAG, "📍 Found ${places.length()} places for '$query'")

            for (i in 0 until places.length()) {
                val place = places.getJSONObject(i)

                val name = place.optJSONObject("displayName")?.optString("text") ?: "Unknown"
                val address = place.optString("formattedAddress", "Unknown")
                val location = place.optJSONObject("location")

                val pLat = location?.optDouble("latitude") ?: 0.0
                val pLng = location?.optDouble("longitude") ?: 0.0

                // Calculate distance
                val distance = calculateDistance(lat, lng, pLat, pLng)

                nearbyPlaces.add(
                    NearbyPlace(
                        name = name,
                        address = address,
                        latitude = pLat,
                        longitude = pLng,
                        distance = distance,
                        placeId = place.optString("id", ""),
                        types = emptyList(),
                        isOpen = false
                    )
                )
            }

            // Sort by distance
            nearbyPlaces.sortBy { it.distance }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Text search failed: ${e.message}")
            e.printStackTrace()
        }

        nearbyPlaces
    }

    /**
     * Calculate distance in meters between two coordinates
     */
    private fun calculateDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Float {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(lat1, lng1, lat2, lng2, results)
        return results[0]
    }

    /**
     * Convert lat/lng to grid key
     */
    private fun getGridKey(latitude: Double, longitude: Double): String {
        val gridLat = (latitude / GRID_SIZE).toInt() * GRID_SIZE
        val gridLng = (longitude / GRID_SIZE).toInt() * GRID_SIZE
        return "grid_%.2f_%.2f".format(gridLat, gridLng)
    }

    /**
     * Calculate distance between two grid keys
     */
    private fun getGridDistance(key1: String, key2: String): Double {
        val parts1 = key1.split("_")
        val parts2 = key2.split("_")

        if (parts1.size != 3 || parts2.size != 3) return Double.MAX_VALUE

        val lat1 = parts1[1].toDoubleOrNull() ?: return Double.MAX_VALUE
        val lng1 = parts1[2].toDoubleOrNull() ?: return Double.MAX_VALUE
        val lat2 = parts2[1].toDoubleOrNull() ?: return Double.MAX_VALUE
        val lng2 = parts2[2].toDoubleOrNull() ?: return Double.MAX_VALUE

        val latDiff = lat1 - lat2
        val lngDiff = lng1 - lng2
        return Math.sqrt(latDiff * latDiff + lngDiff * lngDiff)
    }

    /**
     * Try to find nearby cache entries
     */
    suspend fun getNearbyCache(latitude: Double, longitude: Double): CachedResult? {
        val targetKey = getGridKey(latitude, longitude)

        return mutex.withLock {
            val nearbyCaches = memoryCache.filter { (key, cached) ->
                val distance = getGridDistance(targetKey, key)
                val age = System.currentTimeMillis() - cached.timestamp
                distance <= 2 * GRID_SIZE && age < CACHE_VALIDITY_MS
            }

            if (nearbyCaches.isNotEmpty()) {
                val closest = nearbyCaches.minByOrNull { (key, _) ->
                    getGridDistance(targetKey, key)
                }

                closest?.let {
                    Log.d(TAG, "✅ Found NEARBY cache: ${it.key}")
                    return@withLock it.value
                }
            }

            null
        }
    }

    /**
     * Save cache to disk
     */
    private fun saveCacheToDisk() {
        try {
            val cacheFile = File(context.cacheDir, CACHE_FILE_NAME)
            val jsonArray = JSONArray()

            memoryCache.forEach { (key, cached) ->
                val jsonObject = JSONObject().apply {
                    put("gridKey", key)
                    put("timestamp", cached.timestamp)
                    put("hospitals", placesToJson(cached.hospitals))
                    put("policeStations", placesToJson(cached.policeStations))
                }
                jsonArray.put(jsonObject)
            }

            cacheFile.writeText(jsonArray.toString())
            Log.d(TAG, "💾 Saved ${memoryCache.size} entries to disk")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save cache: ${e.message}")
        }
    }

    /**
     * Load cache from disk
     */
    private fun loadCacheFromDisk() {
        try {
            val cacheFile = File(context.cacheDir, CACHE_FILE_NAME)
            if (!cacheFile.exists()) {
                Log.d(TAG, "No cache file found")
                return
            }

            val jsonArray = JSONArray(cacheFile.readText())
            var loadedCount = 0

            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val timestamp = jsonObject.getLong("timestamp")
                val age = System.currentTimeMillis() - timestamp

                if (age < CACHE_VALIDITY_MS) {
                    val gridKey = jsonObject.getString("gridKey")
                    val hospitals = jsonToPlaces(jsonObject.getJSONArray("hospitals"))
                    val policeStations = jsonToPlaces(jsonObject.getJSONArray("policeStations"))

                    memoryCache[gridKey] = CachedResult(
                        hospitals = hospitals,
                        policeStations = policeStations,
                        timestamp = timestamp,
                        gridKey = gridKey
                    )
                    loadedCount++
                }
            }

            Log.d(TAG, "✅ Loaded $loadedCount cache entries from disk")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load cache: ${e.message}")
        }
    }

    private fun placesToJson(places: List<NearbyPlace>): JSONArray {
        val jsonArray = JSONArray()
        places.forEach { place ->
            val jsonObject = JSONObject().apply {
                put("name", place.name)
                put("address", place.address)
                put("latitude", place.latitude)
                put("longitude", place.longitude)
                put("distance", place.distance)
                put("placeId", place.placeId)
            }
            jsonArray.put(jsonObject)
        }
        return jsonArray
    }

    private fun jsonToPlaces(jsonArray: JSONArray): List<NearbyPlace> {
        val places = mutableListOf<NearbyPlace>()
        for (i in 0 until jsonArray.length()) {
            val jsonObject = jsonArray.getJSONObject(i)
            places.add(
                NearbyPlace(
                    name = jsonObject.getString("name"),
                    address = jsonObject.getString("address"),
                    latitude = jsonObject.getDouble("latitude"),
                    longitude = jsonObject.getDouble("longitude"),
                    distance = jsonObject.getDouble("distance").toFloat(),
                    placeId = jsonObject.getString("placeId"),
                    types = emptyList(),
                    isOpen = false
                )
            )
        }
        return places
    }

    suspend fun clearCache() {
        mutex.withLock {
            memoryCache.clear()
            val cacheFile = File(context.cacheDir, CACHE_FILE_NAME)
            cacheFile.delete()
            Log.d(TAG, "🗑️ Cache cleared")
        }
    }

    fun getCacheStats(): CacheStats {
        val now = System.currentTimeMillis()
        var validEntries = 0
        var expiredEntries = 0

        memoryCache.forEach { (_, cached) ->
            val age = now - cached.timestamp
            if (age < CACHE_VALIDITY_MS) {
                validEntries++
            } else {
                expiredEntries++
            }
        }

        return CacheStats(
            totalEntries = memoryCache.size,
            validEntries = validEntries,
            expiredEntries = expiredEntries,
            cacheHitRate = 0f
        )
    }

    data class CacheStats(
        val totalEntries: Int,
        val validEntries: Int,
        val expiredEntries: Int,
        val cacheHitRate: Float
    )
}