package com.erayoz.uberapp.ui.driver

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erayoz.uberapp.data.repository.AuthRepository
import com.erayoz.uberapp.data.repository.LocationRepository
import com.erayoz.uberapp.data.repository.RideRepository
import com.erayoz.uberapp.data.model.RideRequest
import com.erayoz.uberapp.data.repository.DirectionsRepository
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import javax.inject.Inject

data class DriverMapUiState(
    val isTracking: Boolean = false,
    val currentLocation: LatLng? = null,
    val routeToPickup: List<LatLng> = emptyList(),
    val activeDistance: String = "",
    val activeDuration: String = ""
)

@HiltViewModel
class DriverViewModel @Inject constructor(
    private val locationRepository: LocationRepository,
    private val authRepository: AuthRepository,
    private val rideRepository: RideRepository,
    private val directionsRepository: DirectionsRepository
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
        checkForActiveRide()
    }

    private var activeRideJob: Job? = null
    private var liveMetricsJob: Job? = null
    private var lastMetricsOrigin: LatLng? = null
    private var lastMetricsTarget: LatLng? = null
    private var lastMetricsStatus: String? = null

    private fun checkForActiveRide() {
        val uid = driverId ?: return
        activeRideJob?.cancel()
        activeRideJob = viewModelScope.launch {
            rideRepository.observeActiveRideForDriver(uid).collect { ride ->
                ride?.let {
                    _activeRide.value = it
                    observeActiveRide(it.id)
                }
            }
        }
    }

    private fun observeOwnLocation() {
        val uid = driverId ?: return
        viewModelScope.launch {
            locationRepository.observeDriverLocation(uid).collect { driverLocation ->
                driverLocation?.let {
                    val latLng = LatLng(it.latitude, it.longitude)
                    _uiState.update { state -> state.copy(currentLocation = latLng) }
                    
                    // Update active distance metrics
                    _activeRide.value?.let { ride ->
                        updateActiveMetrics(latLng, ride)
                    }
                }
            }
        }
    }

    private fun updateActiveMetrics(current: LatLng, ride: RideRequest) {
        val target = if (ride.status == "accepted") {
            LatLng(ride.pickupLatitude, ride.pickupLongitude)
        } else {
            LatLng(ride.destinationLatitude, ride.destinationLongitude)
        }

        if (!shouldRefreshLiveMetrics(current, target, ride.status)) return

        liveMetricsJob?.cancel()
        liveMetricsJob = viewModelScope.launch {
            val origin = "${current.latitude},${current.longitude}"
            val destination = "${target.latitude},${target.longitude}"

            directionsRepository.getDirections(origin, destination).onSuccess { response ->
                val leg = response.routes.firstOrNull()?.legs?.firstOrNull() ?: return@onSuccess
                lastMetricsOrigin = current
                lastMetricsTarget = target
                lastMetricsStatus = ride.status
                _uiState.update {
                    it.copy(
                        activeDistance = leg.distance.text,
                        activeDuration = leg.duration.text
                    )
                }
            }
        }
    }

    private fun shouldRefreshLiveMetrics(origin: LatLng, target: LatLng, status: String): Boolean {
        if (status != lastMetricsStatus) return true
        if (!isSameCoordinate(target, lastMetricsTarget)) return true
        val lastOrigin = lastMetricsOrigin ?: return true
        return distanceBetween(origin, lastOrigin) >= 30.0
    }

    private fun isSameCoordinate(first: LatLng, second: LatLng?): Boolean {
        second ?: return false
        return abs(first.latitude - second.latitude) < 0.000001 &&
            abs(first.longitude - second.longitude) < 0.000001
    }

    private fun distanceBetween(first: LatLng, second: LatLng): Float {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(
            first.latitude, first.longitude,
            second.latitude, second.longitude,
            results
        )
        return results[0]
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
        activeRideJob?.cancel()
        activeRideJob = viewModelScope.launch {
            rideRepository.observeRideRequest(rideId).collect { ride ->
                _activeRide.value = ride
                
                // Toggle test offset: Disable during trip to get accurate road distance
                if (ride?.status == "ongoing") {
                    locationRepository.setTestOffsetEnabled(false)
                } else {
                    locationRepository.setTestOffsetEnabled(true)
                }

                if (ride?.status == "accepted") {
                    calculateRouteToPickup(ride)
                } else {
                    _uiState.update { it.copy(routeToPickup = emptyList()) }
                }

                if (ride == null || ride.status == "completed" || ride.status == "cancelled") {
                    _activeRide.value = null
                    liveMetricsJob?.cancel()
                    lastMetricsOrigin = null
                    lastMetricsTarget = null
                    lastMetricsStatus = null
                    _uiState.update { it.copy(activeDistance = "", activeDuration = "") }
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

    private fun calculateRouteToPickup(ride: RideRequest) {
        val driverLoc = _uiState.value.currentLocation ?: return
        val origin = "${driverLoc.latitude},${driverLoc.longitude}"
        val destination = "${ride.pickupLatitude},${ride.pickupLongitude}"
        
        viewModelScope.launch {
            directionsRepository.getDirections(origin, destination).onSuccess { response ->
                val points = response.routes.firstOrNull()?.overviewPolyline?.points
                if (points != null) {
                    val decodedPoints = com.google.maps.android.PolyUtil.decode(points)
                    _uiState.update { it.copy(routeToPickup = decodedPoints) }
                }
            }
        }
    }
}
