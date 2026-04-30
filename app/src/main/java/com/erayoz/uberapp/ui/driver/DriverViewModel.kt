package com.erayoz.uberapp.ui.driver

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erayoz.uberapp.data.repository.AuthRepository
import com.erayoz.uberapp.data.repository.LocationRepository
import com.erayoz.uberapp.data.repository.RideRepository
import com.erayoz.uberapp.data.model.RideRequest
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DriverMapUiState(
    val isTracking: Boolean = false,
    val currentLocation: LatLng? = null
)

@HiltViewModel
class DriverViewModel @Inject constructor(
    private val locationRepository: LocationRepository,
    private val authRepository: AuthRepository,
    private val rideRepository: RideRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DriverMapUiState())
    val uiState: StateFlow<DriverMapUiState> = _uiState.asStateFlow()

    private val _pendingRides = MutableStateFlow<List<RideRequest>>(emptyList())
    val pendingRides: StateFlow<List<RideRequest>> = _pendingRides.asStateFlow()

    private val _activeRide = MutableStateFlow<RideRequest?>(null)
    val activeRide: StateFlow<RideRequest?> = _activeRide.asStateFlow()

    val driverId: String? get() = authRepository.getCurrentUserId()

    init {
        observeOwnLocation()
        observePendingRides()
    }

    private fun observeOwnLocation() {
        val uid = driverId ?: return
        viewModelScope.launch {
            locationRepository.observeDriverLocation(uid).collect { driverLocation ->
                driverLocation?.let {
                    _uiState.update { state ->
                        state.copy(currentLocation = LatLng(it.latitude, it.longitude))
                    }
                }
            }
        }
    }

    private fun observePendingRides() {
        viewModelScope.launch {
            rideRepository.observePendingRides().collect { rides ->
                // Sadece aktif bir sürüşü yoksa bekleyenleri göster
                if (_activeRide.value == null) {
                    _pendingRides.value = rides
                }
            }
        }
    }

    fun acceptRide(ride: RideRequest) {
        val uid = driverId ?: return
        viewModelScope.launch {
            rideRepository.acceptRide(ride.id, uid).onSuccess {
                _activeRide.value = ride.copy(status = "accepted", driverId = uid)
                _pendingRides.value = emptyList()
                observeActiveRide(ride.id)
            }
        }
    }

    private fun observeActiveRide(rideId: String) {
        viewModelScope.launch {
            rideRepository.observeRideRequest(rideId).collect { ride ->
                _activeRide.value = ride
                if (ride == null || ride.status == "completed" || ride.status == "cancelled") {
                    _activeRide.value = null
                    // Sürüş bittiğinde tekrar bekleyenleri dinlemeye başla (zaten init'te var ama listeyi temizlemiştik)
                }
            }
        }
    }

    fun updateRideStatus(status: String) {
        val ride = _activeRide.value ?: return
        viewModelScope.launch {
            rideRepository.updateRideStatus(ride.id, status)
        }
    }

    fun setTracking(tracking: Boolean) {
        _uiState.update { it.copy(isTracking = tracking) }
    }

    fun signOut() {
        authRepository.signOut()
    }
}
