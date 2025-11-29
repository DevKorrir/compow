package com.example.compow.data

/**
 * Represents a nearby emergency facility (hospital or police station)
 */
data class NearbyPlace(
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val distance: Float, // in kilometers
    val placeId: String,
    val types: List<String>,
    val isOpen: Boolean = false
)