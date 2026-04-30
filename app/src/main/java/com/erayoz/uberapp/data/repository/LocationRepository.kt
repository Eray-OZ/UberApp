package com.erayoz.uberapp.data.repository

import com.erayoz.uberapp.data.model.DriverLocation
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationRepository @Inject constructor(
    private val database: FirebaseDatabase
) {
    private val driversRef = database.getReference("driver_locations")

    fun updateDriverLocation(location: DriverLocation) {
        driversRef.child(location.driverId).setValue(location)
    }

    fun observeDriverLocation(driverId: String): Flow<DriverLocation?> = callbackFlow {
        val ref = driversRef.child(driverId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.getValue(DriverLocation::class.java))
            }
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    fun observeAllDriverLocations(): Flow<List<DriverLocation>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val locations = snapshot.children.mapNotNull {
                    it.getValue(DriverLocation::class.java)
                }
                trySend(locations)
            }
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        driversRef.addValueEventListener(listener)
        awaitClose { driversRef.removeEventListener(listener) }
    }
}
