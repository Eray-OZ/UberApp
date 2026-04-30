package com.erayoz.uberapp.data.repository

import com.erayoz.uberapp.data.model.RideRequest
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RideRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val database: FirebaseDatabase
) {
    private val ridesCollection = firestore.collection("rides")
    private val rideEventsRef = database.getReference("ride_events")

    suspend fun createRideRequest(rideRequest: RideRequest): Result<String> {
        return try {
            val docRef = ridesCollection.document()
            val rideWithId = rideRequest.copy(id = docRef.id)
            docRef.set(rideWithId).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observeRideRequest(rideId: String): Flow<RideRequest?> = callbackFlow {
        val listener = ridesCollection.document(rideId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.toObject(RideRequest::class.java))
        }
        awaitClose { listener.remove() }
    }

    fun observePendingRides(): Flow<List<RideRequest>> = callbackFlow {
        val listener = ridesCollection
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val rides = snapshot?.documents?.mapNotNull { it.toObject(RideRequest::class.java) } ?: emptyList()
                trySend(rides)
            }
        awaitClose { listener.remove() }
    }

    suspend fun acceptRide(rideId: String, driverId: String): Result<Unit> {
        return try {
            ridesCollection.document(rideId).update(
                mapOf(
                    "status" to "accepted",
                    "driverId" to driverId
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateRideStatus(rideId: String, status: String): Result<Unit> {
        return try {
            ridesCollection.document(rideId).update("status", status).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun cancelRide(rideId: String): Result<Unit> {
        return updateRideStatus(rideId, "cancelled")
    }

    fun observeActiveRideForPassenger(passengerId: String): Flow<RideRequest?> = callbackFlow {
        val listener = ridesCollection
            .whereEqualTo("passengerId", passengerId)
            .whereIn("status", listOf("pending", "accepted", "ongoing"))
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val doc = snapshot?.documents?.firstOrNull()
                val ride = doc?.toObject(RideRequest::class.java)?.copy(id = doc.id)
                trySend(ride)
            }
        awaitClose { listener.remove() }
    }

    fun observeActiveRideForDriver(driverId: String): Flow<RideRequest?> = callbackFlow {
        val listener = ridesCollection
            .whereEqualTo("driverId", driverId)
            .whereIn("status", listOf("accepted", "ongoing"))
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val doc = snapshot?.documents?.firstOrNull()
                val ride = doc?.toObject(RideRequest::class.java)?.copy(id = doc.id)
                trySend(ride)
            }
        awaitClose { listener.remove() }
    }
}
