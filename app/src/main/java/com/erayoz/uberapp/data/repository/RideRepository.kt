package com.erayoz.uberapp.data.repository

import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RideRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val database: FirebaseDatabase
) {
    fun ridesCollectionPath(): String = firestore.collection("rides").path

    fun rideEventsPath(): String = database.getReference("ride_events").path.toString()
}
