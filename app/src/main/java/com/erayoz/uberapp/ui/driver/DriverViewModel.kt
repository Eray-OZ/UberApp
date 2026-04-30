package com.erayoz.uberapp.ui.driver

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erayoz.uberapp.data.repository.AuthRepository
import com.erayoz.uberapp.data.repository.LocationRepository
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
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DriverMapUiState())
    val uiState: StateFlow<DriverMapUiState> = _uiState.asStateFlow()

    val driverId: String? get() = authRepository.getCurrentUserId()

    init {
        observeOwnLocation()
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

    fun setTracking(tracking: Boolean) {
        _uiState.update { it.copy(isTracking = tracking) }
    }

    fun signOut() {
        authRepository.signOut()
    }
}
