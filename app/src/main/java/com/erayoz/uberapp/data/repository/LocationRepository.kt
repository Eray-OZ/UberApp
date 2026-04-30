package com.erayoz.uberapp.data.repository

import com.google.firebase.database.FirebaseDatabase
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationRepository @Inject constructor(
    private val database: FirebaseDatabase
) {
    fun driverLocationsPath(): String = database.getReference("driver_locations").path.toString()
}
