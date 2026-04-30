package com.erayoz.uberapp.data.model

data class DriverLocation(
    val driverId: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val timestamp: Long = 0L
)
