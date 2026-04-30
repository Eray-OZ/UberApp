package com.erayoz.uberapp.ui.driver

import androidx.lifecycle.ViewModel
import com.erayoz.uberapp.data.repository.LocationRepository
import com.erayoz.uberapp.data.repository.RideRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class DriverViewModel @Inject constructor(
    private val rideRepository: RideRepository,
    private val locationRepository: LocationRepository
) : ViewModel() {
    val title: String = "Driver Map"
}
