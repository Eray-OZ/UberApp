package com.erayoz.uberapp.ui.passenger

import android.annotation.SuppressLint
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erayoz.uberapp.data.model.RideRequest
import com.erayoz.uberapp.data.repository.AuthRepository
import com.erayoz.uberapp.data.repository.DirectionsRepository
import com.erayoz.uberapp.data.repository.PlacesRepository
import com.erayoz.uberapp.data.repository.RideRepository
import com.erayoz.uberapp.data.repository.LocationRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.libraries.places.api.model.AutocompletePrediction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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
    val rideStatus: String? = null,
    val driverId: String? = null,
    val driverLocation: LatLng? = null,
    val destinationAddress: String = "",
    val driverDistance: String = ""
)

@HiltViewModel
class PassengerViewModel @Inject constructor(
    private val fusedLocationClient: FusedLocationProviderClient,
    private val authRepository: AuthRepository,
    private val placesRepository: PlacesRepository,
    private val directionsRepository: DirectionsRepository,
    private val rideRepository: RideRepository,
    private val locationRepository: LocationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PassengerMapUiState())
    val uiState: StateFlow<PassengerMapUiState> = _uiState.asStateFlow()

    init {
        placesRepository.createNewSession()
        checkForActiveRide()
    }

    private fun checkForActiveRide() {
        val userId = authRepository.getCurrentUserId() ?: return
        viewModelScope.launch {
            rideRepository.observeActiveRideForPassenger(userId).collect { ride ->
                ride?.let {
                    _uiState.update { state -> state.copy(
                        rideId = it.id,
                        rideStatus = it.status,
                        driverId = it.driverId,
                        destinationAddress = it.destinationAddress,
                        estimatedDistance = it.distanceText,
                        estimatedDuration = it.durationText,
                        polylinePoints = com.google.maps.android.PolyUtil.decode(it.polylinePoints),
                        destinationLocation = LatLng(it.destinationLatitude, it.destinationLongitude)
                    ) }
                    observeRide(it.id)
                }
            }
        }
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
        val address = prediction.getFullText(null).toString()
        _uiState.update { it.copy(
            searchQuery = address,
            destinationAddress = address,
            searchPredictions = emptyList()
        ) }
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
            destinationAddress = state.destinationAddress,
            distanceText = state.estimatedDistance,
            durationText = state.estimatedDuration,
            price = state.estimatedPrice,
            polylinePoints = com.google.maps.android.PolyUtil.encode(state.polylinePoints),
            status = "pending"
        )

        viewModelScope.launch {
            rideRepository.createRideRequest(request).onSuccess { id ->
                _uiState.update { it.copy(rideId = id, rideStatus = "pending") }
                observeRide(id)
            }
        }
    }

    private var rideObservationJob: Job? = null
    private var driverLocationJob: Job? = null

    private fun observeRide(id: String) {
        rideObservationJob?.cancel()
        rideObservationJob = viewModelScope.launch {
            rideRepository.observeRideRequest(id).collect { ride ->
                _uiState.update { it.copy(
                    rideStatus = ride?.status,
                    driverId = ride?.driverId
                ) }
                
                if (ride?.driverId != null && (ride.status == "accepted" || ride.status == "ongoing")) {
                    // Only start observing if not already observing THIS driver
                    if (driverLocationJob == null || _uiState.value.driverId != ride.driverId) {
                        observeDriverLocation(ride.driverId)
                    }
                } else {
                    driverLocationJob?.cancel()
                    driverLocationJob = null
                    _uiState.update { it.copy(driverLocation = null, driverDistance = "") }
                }

                if (ride == null || ride.status == "completed" || ride.status == "cancelled") {
                    // Stop tracking driver immediately
                    driverLocationJob?.cancel()
                    driverLocationJob = null
                    
                    if (ride?.status == "completed") {
                        // Keep 'completed' status for a moment to show the UI, but clear tracking data
                        _uiState.update { it.copy(
                            driverLocation = null,
                            driverDistance = "",
                            driverId = null
                        ) }
                        
                        // After a short delay or manual action, we could clear everything.
                        // For now, let's ensure it doesn't stay 'ongoing'
                        viewModelScope.launch {
                            kotlinx.coroutines.delay(5000) // Show 'Completed' for 5 secs
                            if (_uiState.value.rideStatus == "completed") {
                                clearRoute()
                                _uiState.update { it.copy(rideId = null, rideStatus = null) }
                                rideObservationJob?.cancel()
                            }
                        }
                    } else if (ride?.status == "cancelled" || ride == null) {
                        clearRoute()
                        _uiState.update { it.copy(rideId = null, rideStatus = null, driverId = null, driverLocation = null, driverDistance = "") }
                        rideObservationJob?.cancel()
                    }
                }
            }
        }
    }

    private fun observeDriverLocation(driverId: String) {
        driverLocationJob?.cancel()
        driverLocationJob = viewModelScope.launch {
            locationRepository.observeDriverLocation(driverId).collect { driverLoc ->
                driverLoc?.let { loc ->
                    val driverLatLng = LatLng(loc.latitude, loc.longitude)
                    val passengerLoc = _uiState.value.currentLocation
                    val distanceText = if (passengerLoc != null) {
                        val results = FloatArray(1)
                        android.location.Location.distanceBetween(
                            passengerLoc.latitude, passengerLoc.longitude,
                            loc.latitude, loc.longitude,
                            results
                        )
                        val distanceInKm = results[0] / 1000.0
                        "%.1f km away".format(distanceInKm)
                    } else ""

                    _uiState.update { it.copy(
                        driverLocation = driverLatLng,
                        driverDistance = distanceText
                    ) }
                }
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
