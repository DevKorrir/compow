package com.example.compow.utils

import android.content.Context
import android.util.Log
import com.example.compow.data.NearbyPlace
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

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
        // This means results within 1km area share same cache
        private const val GRID_SIZE = 0.01
        
        // Cache validity: 7 days (POIs don't change frequently)
        private const val CACHE_VALIDITY_MS = 7L * 24 * 60 * 60 * 1000 // 7 days
        
        // Maximum cache entries to prevent storage overflow
        private const val MAX_CACHE_ENTRIES = 500
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
     * Uses geographic grid system to maximize cache hits
     */
    suspend fun getCachedResults(latitude: Double, longitude: Double): CachedResult? {
        val gridKey = getGridKey(latitude, longitude)
        
        return mutex.withLock {
            val cached = memoryCache[gridKey]
            
            if (cached != null) {
                val age = System.currentTimeMillis() - cached.timestamp
                if (age < CACHE_VALIDITY_MS) {
                    Log.d(TAG, "✅ Cache HIT for grid $gridKey (age: ${age / 1000 / 60} minutes)")
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
            // Prevent cache overflow
            if (memoryCache.size >= MAX_CACHE_ENTRIES) {
                // Remove oldest entry
                val oldestKey = memoryCache.minByOrNull { it.value.timestamp }?.key
                oldestKey?.let { memoryCache.remove(it) }
            }
            
            memoryCache[gridKey] = cached
            Log.d(TAG, "💾 Cached results for grid $gridKey (${memoryCache.size} entries)")
        }

        // Persist to disk asynchronously
        saveCacheToDisk()
    }

    /**
     * Convert lat/lng to grid key
     * Example: lat=-0.0469, lng=37.6494 -> grid_-0.04_37.64
     */
    private fun getGridKey(latitude: Double, longitude: Double): String {
        val gridLat = (latitude / GRID_SIZE).toInt() * GRID_SIZE
        val gridLng = (longitude / GRID_SIZE).toInt() * GRID_SIZE
        return "grid_%.2f_%.2f".format(gridLat, gridLng)
    }

    /**
     * Calculate distance between two grid keys (for nearby cache lookup)
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
     * Try to find nearby cache entries if exact match not found
     */
    suspend fun getNearbyCache(latitude: Double, longitude: Double): CachedResult? {
        val targetKey = getGridKey(latitude, longitude)
        
        return mutex.withLock {
            // Look for cache entries within 2 grid units (~2km)
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
                    Log.d(TAG, "✅ Found NEARBY cache: ${it.key} (${nearbyCaches.size} candidates)")
                    return@withLock it.value
                }
            }
            
            null
        }
    }

    /**
     * Save cache to disk for persistence across app restarts
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
            Log.d(TAG, "💾 Saved ${memoryCache.size} cache entries to disk")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save cache to disk: ${e.message}")
        }
    }

    /**
     * Load cache from disk on app start
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
                
                // Only load non-expired entries
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
            Log.e(TAG, "Failed to load cache from disk: ${e.message}")
        }
    }

    /**
     * Convert places to JSON
     */
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

    /**
     * Convert JSON to places
     */
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

    /**
     * Clear all cache (for testing or manual reset)
     */
    suspend fun clearCache() {
        mutex.withLock {
            memoryCache.clear()
            val cacheFile = File(context.cacheDir, CACHE_FILE_NAME)
            cacheFile.delete()
            Log.d(TAG, "🗑️ Cache cleared")
        }
    }

    /**
     * Get cache statistics
     */
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
            cacheHitRate = 0f // This would need to be tracked separately
        )
    }

    data class CacheStats(
        val totalEntries: Int,
        val validEntries: Int,
        val expiredEntries: Int,
        val cacheHitRate: Float
    )
}