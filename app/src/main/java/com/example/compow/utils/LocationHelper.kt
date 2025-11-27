package com.example.compow.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class LocationHelper(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    /**
     * Get the last known location
     * Returns null if location is not available or permission not granted
     */
    suspend fun getLastLocation(): Location? {
        if (!hasLocationPermission()) {
            return null
        }

        return try {
            fusedLocationClient.lastLocation.await()
        } catch (e: SecurityException) {
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get current location with high accuracy
     * This requests a fresh location update
     */
    suspend fun getCurrentLocation(): Location? {
        if (!hasLocationPermission()) {
            return null
        }

        return try {
            val cancellationTokenSource = CancellationTokenSource()
            val location = fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationTokenSource.token
            ).await()

            location
        } catch (e: SecurityException) {
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get location with fallback
     * Tries current location first, then falls back to last known location
     */
    suspend fun getLocationWithFallback(): Location? {
        return getCurrentLocation() ?: getLastLocation()
    }

    /**
     * Check if location services are enabled
     */
    fun isLocationEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    /**
     * Check if app has location permission
     */
    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Format location as Google Maps URL
     */
    fun getGoogleMapsUrl(location: Location): String {
        return "https://maps.google.com/?q=${location.latitude},${location.longitude}"
    }

    /**
     * Format location as Google Maps URL from lat/lng
     */
    fun getGoogleMapsUrl(latitude: Double, longitude: Double): String {
        return "https://maps.google.com/?q=$latitude,$longitude"
    }

    /**
     * Format location as readable string
     */
    fun formatLocation(location: Location): String {
        return "Lat: ${String.format("%.6f", location.latitude)}, " +
                "Lng: ${String.format("%.6f", location.longitude)}"
    }

    /**
     * Calculate distance between two locations in meters
     */
    fun calculateDistance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }

    /**
     * Calculate distance between two locations in kilometers
     */
    fun calculateDistanceKm(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Float {
        return calculateDistance(lat1, lon1, lat2, lon2) / 1000f
    }

    /**
     * Check if location is accurate enough (within 100 meters)
     */
    fun isLocationAccurate(location: Location): Boolean {
        return location.hasAccuracy() && location.accuracy <= 100f
    }

    /**
     * Get location update settings
     */
    fun getLocationRequest(): LocationRequest {
        return LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
            .setWaitForAccurateLocation(false)
            .setMinUpdateIntervalMillis(5000)
            .setMaxUpdateDelayMillis(15000)
            .build()
    }

    companion object {
        const val DEFAULT_LOCATION_TIMEOUT = 30000L // 30 seconds
        const val LOCATION_ACCURACY_THRESHOLD = 100f // 100 meters

        // Meru, Kenya default coordinates
        const val DEFAULT_LATITUDE = -0.0469
        const val DEFAULT_LONGITUDE = 37.6494

        /**
         * Get default location (Meru, Kenya)
         */
        fun getDefaultLocation(): Pair<Double, Double> {
            return Pair(DEFAULT_LATITUDE, DEFAULT_LONGITUDE)
        }

        /**
         * Create a Location object from coordinates
         */
        fun createLocation(latitude: Double, longitude: Double): Location {
            return Location("").apply {
                this.latitude = latitude
                this.longitude = longitude
            }
        }
    }
}