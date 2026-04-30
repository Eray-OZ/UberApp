package com.erayoz.uberapp.data.repository

import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlacesRepository @Inject constructor(
    private val placesClient: PlacesClient
) {
    private var sessionToken: AutocompleteSessionToken? = null

    fun createNewSession() {
        sessionToken = AutocompleteSessionToken.newInstance()
    }

    suspend fun getAutocompletePredictions(query: String): List<AutocompletePrediction> {
        if (query.isBlank()) return emptyList()
        
        val request = FindAutocompletePredictionsRequest.builder()
            .setSessionToken(sessionToken)
            .setQuery(query)
            .build()

        return try {
            val response = placesClient.findAutocompletePredictions(request).await()
            response.autocompletePredictions
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getPlaceCoordinates(placeId: String): LatLng? {
        val placeFields = listOf(Place.Field.LAT_LNG)
        val request = FetchPlaceRequest.builder(placeId, placeFields).build()

        return try {
            val response = placesClient.fetchPlace(request).await()
            response.place.latLng
        } catch (e: Exception) {
            null
        }
    }
}
