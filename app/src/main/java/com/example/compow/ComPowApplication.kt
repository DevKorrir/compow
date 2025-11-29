package com.example.compow

import android.app.Application
import android.content.pm.PackageManager
import com.google.android.libraries.places.api.Places
import android.util.Log // Add this import

class ComPowApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Pull the API key from the manifest (safe + private)
        val apiKey = try {
            val ai = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            ai.metaData.getString("com.google.android.geo.API_KEY")
        } catch (e: Exception) {
            // FIX: Log the exception to help with debugging.
            Log.e("ComPowApplication", "Failed to load Google Maps API key from manifest", e)
            null
        }


        if (!apiKey.isNullOrEmpty() && !Places.isInitialized()) {
            Places.initialize(applicationContext, apiKey)
        }
    }
}
