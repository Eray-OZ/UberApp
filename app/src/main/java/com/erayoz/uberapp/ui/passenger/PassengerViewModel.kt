package com.erayoz.uberapp.ui.passenger

import android.annotation.SuppressLint
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erayoz.uberapp.data.repository.AuthRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class PassengerMapUiState(
    val currentLocation: LatLng? = null,
    val isLoadingLocation: Boolean = false
)

@HiltViewModel
class PassengerViewModel @Inject constructor(
    private val fusedLocationClient: FusedLocationProviderClient,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PassengerMapUiState())
    val uiState: StateFlow<PassengerMapUiState> = _uiState.asStateFlow()

    @SuppressLint("MissingPermission")
    fun fetchCurrentLocation() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingLocation = true) }
            
            val cts = CancellationTokenSource()
            try {
                // 1. Önce güncel konumu dene
                val location = fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    cts.token
                ).await()
                
                if (location != null) {
                    _uiState.update { state ->
                        state.copy(
                            currentLocation = LatLng(location.latitude, location.longitude),
                            isLoadingLocation = false
                        )
                    }
                } else {
                    // 2. Güncel konum yoksa son bilinen konumu dene
                    val lastLocation = fusedLocationClient.lastLocation.await()
                    _uiState.update { state ->
                        state.copy(
                            currentLocation = lastLocation?.let { LatLng(it.latitude, it.longitude) },
                            isLoadingLocation = false
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingLocation = false) }
            } finally {
                cts.cancel()
            }
        }
    }

    fun signOut() {
        authRepository.signOut()
    }
}
