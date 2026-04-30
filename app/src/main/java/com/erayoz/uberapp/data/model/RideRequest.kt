package com.erayoz.uberapp.data.model

data class RideRequest(
    val id: String = "",
    val passengerId: String = "",
    val driverId: String? = null,
    val pickupLatitude: Double = 0.0,
    val pickupLongitude: Double = 0.0,
    val destinationLatitude: Double = 0.0,
    val destinationLongitude: Double = 0.0,
    val pickupAddress: String = "",
    val destinationAddress: String = "",
    val status: String = "pending", // pending, accepted, ongoing, completed, cancelled
    val price: Double = 0.0,
    val distanceText: String = "",
    val durationText: String = "",
    val polylinePoints: String = ""
)
