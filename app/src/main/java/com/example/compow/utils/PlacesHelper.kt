package com.example.compow.utils

import android.Manifest
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import com.example.compow.data.NearbyPlace
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FindCurrentPlaceRequest
import com.google.android.libraries.places.api.net.PlacesClient
import kotlinx.coroutines.tasks.await

class PlacesHelper(context: Context) {

    private val placesClient: PlacesClient = Places.createClient(context)

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    suspend fun findNearbyPlaces(location: LatLng): Pair<List<NearbyPlace>, List<NearbyPlace>> {
        val placeFields = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.ADDRESS, Place.Field.LAT_LNG, Place.Field.TYPES, Place.Field.OPENING_HOURS)
        val request = FindCurrentPlaceRequest.newInstance(placeFields)

        return try {
            val response = placesClient.findCurrentPlace(request).await()
            val hospitals = mutableListOf<NearbyPlace>()
            val policeStations = mutableListOf<NearbyPlace>()

            response.placeLikelihoods.forEach { placeLikelihood ->
                val place = placeLikelihood.place
                val placeTypes = place.types ?: emptyList()

                val nearbyPlace = NearbyPlace(
                    name = place.name ?: "",
                    address = place.address ?: "",
                    latitude = place.latLng?.latitude ?: 0.0,
                    longitude = place.latLng?.longitude ?: 0.0,
                    distance = 0f, // This will be calculated later
                    placeId = place.id ?: "",
                    types = placeTypes.map { it.name },
                    isOpen = place.isOpen ?: false
                )

                if (placeTypes.contains(Place.Type.HOSPITAL)) {
                    hospitals.add(nearbyPlace)
                } else if (placeTypes.contains(Place.Type.POLICE)) {
                    policeStations.add(nearbyPlace)
                }
            }
            Pair(hospitals, policeStations)
        } catch (e: Exception) {
            Log.e("PlacesHelper", "Error finding places: ${e.message}")
            Pair(emptyList(), emptyList())
        }
    }
}