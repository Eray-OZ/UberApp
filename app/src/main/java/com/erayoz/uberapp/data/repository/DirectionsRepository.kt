package com.erayoz.uberapp.data.repository

import com.erayoz.uberapp.BuildConfig
import com.erayoz.uberapp.data.remote.DirectionsApiService
import com.erayoz.uberapp.data.remote.DirectionsResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DirectionsRepository @Inject constructor(
    private val apiService: DirectionsApiService
) {
    suspend fun getDirections(origin: String, destination: String): Result<DirectionsResponse> {
        return try {
            val response = apiService.getDirections(
                origin = origin,
                destination = destination,
                apiKey = BuildConfig.MAPS_API_KEY
            )
            if (response.status == "OK") {
                Result.success(response)
            } else {
                Result.failure(Exception("Directions API error: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
