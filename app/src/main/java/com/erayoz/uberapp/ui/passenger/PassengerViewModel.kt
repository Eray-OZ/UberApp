package com.erayoz.uberapp.ui.passenger

import android.annotation.SuppressLint
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erayoz.uberapp.data.model.RideRequest
import com.erayoz.uberapp.data.repository.AuthRepository
import com.erayoz.uberapp.data.repository.DirectionsRepository
import com.erayoz.uberapp.data.repository.PlacesRepository
import com.erayoz.uberapp.data.repository.RideRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.libraries.places.api.model.AutocompletePrediction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class PassengerMapUiState(
    val currentLocation: LatLng? = null,
    val isLoadingLocation: Boolean = false,
    val searchQuery: String = "",
    val searchPredictions: List<AutocompletePrediction> = emptyList(),
    val destinationLocation: LatLng? = null,
    val polylinePoints: List<LatLng> = emptyList(),
    val estimatedDistance: String = "",
    val estimatedDuration: String = "",
    val estimatedPrice: Double = 0.0,
    val rideId: String? = null,
    val rideStatus: String? = null
)

@HiltViewModel
class PassengerViewModel @Inject constructor(
    private val fusedLocationClient: FusedLocationProviderClient,
    private val authRepository: AuthRepository,
    private val placesRepository: PlacesRepository,
    private val directionsRepository: DirectionsRepository,
    private val rideRepository: RideRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PassengerMapUiState())
    val uiState: StateFlow<PassengerMapUiState> = _uiState.asStateFlow()

    init {
        placesRepository.createNewSession()
    }

    @SuppressLint("MissingPermission")
    fun fetchCurrentLocation() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingLocation = true) }
            val cts = CancellationTokenSource()
            try {
                val location = fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token).await()
                if (location != null) {
                    _uiState.update { it.copy(currentLocation = LatLng(location.latitude, location.longitude), isLoadingLocation = false) }
                } else {
                    val lastLoc = fusedLocationClient.lastLocation.await()
                    _uiState.update { it.copy(currentLocation = lastLoc?.let { LatLng(it.latitude, it.longitude) }, isLoadingLocation = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingLocation = false) }
            } finally {
                cts.cancel()
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        viewModelScope.launch {
            val predictions = placesRepository.getAutocompletePredictions(query)
            _uiState.update { it.copy(searchPredictions = predictions) }
        }
    }

    fun selectDestination(prediction: AutocompletePrediction) {
        _uiState.update { it.copy(searchQuery = prediction.getFullText(null).toString(), searchPredictions = emptyList()) }
        viewModelScope.launch {
            val coords = placesRepository.getPlaceCoordinates(prediction.placeId)
            coords?.let { dest ->
                _uiState.update { it.copy(destinationLocation = dest) }
                calculateRoute(dest)
            }
        }
    }

    private fun calculateRoute(destination: LatLng) {
        val origin = _uiState.value.currentLocation ?: return
        viewModelScope.launch {
            val originStr = "${origin.latitude},${origin.longitude}"
            val destStr = "${destination.latitude},${destination.longitude}"
            
            directionsRepository.getDirections(originStr, destStr).onSuccess { response ->
                val route = response.routes.firstOrNull()
                val leg = route?.legs?.firstOrNull()
                
                if (route != null && leg != null) {
                    val points = com.google.maps.android.PolyUtil.decode(route.overviewPolyline.points)
                    _uiState.update { it.copy(
                        polylinePoints = points,
                        estimatedDistance = leg.distance.text,
                        estimatedDuration = leg.duration.text,
                        estimatedPrice = (leg.distance.value / 1000.0) * 15.0 // Örnek: KM başı 15 TL
                    ) }
                }
            }
        }
    }

    fun requestRide() {
        val state = _uiState.value
        val userId = authRepository.getCurrentUserId() ?: return
        val pickup = state.currentLocation ?: return
        val dest = state.destinationLocation ?: return

        val request = RideRequest(
            passengerId = userId,
            pickupLatitude = pickup.latitude,
            pickupLongitude = pickup.longitude,
            destinationLatitude = dest.latitude,
            destinationLongitude = dest.longitude,
            distanceText = state.estimatedDistance,
            durationText = state.estimatedDuration,
            price = state.estimatedPrice,
            polylinePoints = com.google.maps.android.PolyUtil.encode(state.polylinePoints)
        )

        viewModelScope.launch {
            rideRepository.createRideRequest(request).onSuccess { id ->
                _uiState.update { it.copy(rideId = id, rideStatus = "pending") }
                observeRide(id)
            }
        }
    }

    private fun observeRide(id: String) {
        viewModelScope.launch {
            rideRepository.observeRideRequest(id).collect { ride ->
                _uiState.update { it.copy(rideStatus = ride?.status) }
            }
        }
    }

    fun clearRoute() {
        _uiState.update { it.copy(
            destinationLocation = null,
            polylinePoints = emptyList(),
            searchQuery = "",
            estimatedDistance = "",
            estimatedDuration = "",
            estimatedPrice = 0.0
        ) }
    }

    fun signOut() {
        authRepository.signOut()
    }
}
