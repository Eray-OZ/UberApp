package com.erayoz.uberapp.ui.passenger

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.erayoz.uberapp.ui.common.LocationPermissionHelper
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.*

@Composable
fun PassengerMapScreen(
    onLogout: () -> Unit,
    viewModel: PassengerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    LocationPermissionHelper(
        onPermissionGranted = {
            val cameraPositionState = rememberCameraPositionState {
                position = CameraPosition.fromLatLngZoom(LatLng(41.0082, 28.9784), 10f)
            }

            LaunchedEffect(Unit) {
                viewModel.fetchCurrentLocation()
            }

            // Camera update based on ride state
            LaunchedEffect(uiState.rideStatus, uiState.currentLocation, uiState.driverLocation, uiState.destinationLocation) {
                // Don't override user manual interaction
                if (cameraPositionState.isMoving) return@LaunchedEffect

                val points = mutableListOf<LatLng>()
                fun addIfValid(latLng: LatLng?) {
                    if (latLng != null && latLng.latitude != 0.0 && latLng.longitude != 0.0) {
                        points.add(latLng)
                    }
                }

                when (uiState.rideStatus) {
                    "pending" -> {
                        uiState.currentLocation?.let { 
                            if (it.latitude != 0.0) {
                                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(it, 15f))
                                return@LaunchedEffect
                            }
                        }
                    }
                    "accepted" -> {
                        addIfValid(uiState.currentLocation)
                        addIfValid(uiState.driverLocation)
                    }
                    "ongoing" -> {
                        addIfValid(uiState.driverLocation)
                        addIfValid(uiState.destinationLocation)
                    }
                    else -> {
                        if (uiState.polylinePoints.isNotEmpty()) {
                            uiState.polylinePoints.forEach { addIfValid(it) }
                        } else {
                            uiState.currentLocation?.let { 
                                if (it.latitude != 0.0) {
                                    cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(it, 15f))
                                    return@LaunchedEffect
                                }
                            }
                        }
                    }
                }

                if (points.isNotEmpty()) {
                    val distinctPoints = points.distinct()
                    if (distinctPoints.size >= 2) {
                        val builder = LatLngBounds.Builder()
                        distinctPoints.forEach { builder.include(it) }
                        val bounds = builder.build()
                        
                        // Check if points are too close (prevent weird zoom)
                        val results = FloatArray(1)
                        android.location.Location.distanceBetween(
                            distinctPoints[0].latitude, distinctPoints[0].longitude,
                            distinctPoints[1].latitude, distinctPoints[1].longitude,
                            results
                        )
                        
                        if (results[0] < 50f) { // Closer than 50 meters
                            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(distinctPoints[0], 15f))
                        } else {
                            cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 150))
                        }
                    } else if (distinctPoints.size == 1) {
                        cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(distinctPoints[0], 15f))
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                // Map as Background
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    properties = MapProperties(isMyLocationEnabled = true),
                    uiSettings = MapUiSettings(myLocationButtonEnabled = true)
                ) {
                    val personIcon = remember(context) { com.erayoz.uberapp.util.BitmapUtils.bitmapDescriptorFromVector(context, com.erayoz.uberapp.R.drawable.ic_person) }
                    val carIcon = remember(context) { com.erayoz.uberapp.util.BitmapUtils.bitmapDescriptorFromVector(context, com.erayoz.uberapp.R.drawable.ic_car) }
                    val destIcon = remember(context) { com.erayoz.uberapp.util.BitmapUtils.bitmapDescriptorFromVector(context, com.erayoz.uberapp.R.drawable.ic_destination_pin) }

                    // Pickup Marker (The Passenger)
                    uiState.currentLocation?.let {
                        Marker(
                            state = MarkerState(position = it),
                            title = "You (Passenger)",
                            icon = personIcon
                        )
                    }

                    if (uiState.polylinePoints.isNotEmpty()) {
                        Polyline(
                            points = uiState.polylinePoints,
                            color = Color.Blue,
                            width = 12f
                        )
                        uiState.destinationLocation?.let {
                            Marker(
                                state = MarkerState(position = it),
                                title = "Destination",
                                icon = destIcon
                            )
                        }
                    }

                    // Live Driver Marker
                    uiState.driverLocation?.let {
                        Marker(
                            state = MarkerState(position = it),
                            title = "Your Driver (En Route)",
                            icon = carIcon
                        )
                    }
                }

                // Overlay UI
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .statusBarsPadding()
                ) {
                    // Search Bar (Only show if no active ride or if pending)
                    if (uiState.rideStatus == null) {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = viewModel::onSearchQueryChanged,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium),
                            placeholder = { Text("Where to?") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = {
                                if (uiState.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = viewModel::clearRoute) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear")
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            ),
                            shape = MaterialTheme.shapes.medium
                        )

                        // Search Results
                        if (uiState.searchPredictions.isNotEmpty()) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                shape = MaterialTheme.shapes.medium,
                                tonalElevation = 8.dp
                            ) {
                                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                                    items(uiState.searchPredictions) { prediction ->
                                        ListItem(
                                            headlineContent = { Text(prediction.getPrimaryText(null).toString()) },
                                            supportingContent = { Text(prediction.getSecondaryText(null).toString()) },
                                            modifier = Modifier.clickable {
                                                viewModel.selectDestination(prediction)
                                            }
                                        )
                                        HorizontalDivider()
                                    }
                                }
                            }
                        }
                    }
                }

                // Logout Button
                IconButton(
                    onClick = {
                        viewModel.signOut()
                        onLogout()
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 80.dp, end = 16.dp)
                        .statusBarsPadding(),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Logout")
                }

                // Estimation Card
                if (uiState.polylinePoints.isNotEmpty() && uiState.rideStatus == null) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Estimated Price", style = MaterialTheme.typography.labelMedium)
                                    Text("₺${"%.2f".format(uiState.estimatedPrice)}", style = MaterialTheme.typography.headlineSmall)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Distance: ${uiState.estimatedDistance}")
                                    Text("Duration: ${uiState.estimatedDuration}")
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = viewModel::requestRide,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Request Uber")
                            }
                        }
                    }
                }

                // Ride Status Overlay
                uiState.rideStatus?.let { status ->
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth(),
                        tonalElevation = 8.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val statusText = when (status) {
                                "pending" -> "Finding a driver..."
                                "accepted" -> "Driver is on the way!"
                                "ongoing" -> "En route to destination"
                                "completed" -> "Arrived at destination"
                                "cancelled" -> "Ride Cancelled"
                                else -> "Status: ${status.replaceFirstChar { it.uppercase() }}"
                            }
                            
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            
                            if (status == "pending") {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
                            }
                            
                            if (status == "accepted" || status == "ongoing") {
                                if (uiState.driverDistance.isNotEmpty()) {
                                    Text(
                                        text = uiState.driverDistance,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Text(
                                    "Estimated arrival: ${uiState.estimatedDuration}",
                                    modifier = Modifier.padding(top = 4.dp),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                // Loading bar for current location
                if (uiState.isLoadingLocation && uiState.currentLocation == null) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.Center),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            Text("Finding location...", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        onPermissionDenied = {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Location permission is required to see the map.")
            }
        }
    )
}
