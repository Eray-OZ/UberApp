package com.erayoz.uberapp.data.model

data class RideRequest(
    val id: String = "",
    val passengerId: String = "",
    val pickupLatitude: Double = 0.0,
    val pickupLongitude: Double = 0.0,
    val destinationLatitude: Double = 0.0,
    val destinationLongitude: Double = 0.0,
    val status: String = "pending"
)
