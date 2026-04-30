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

    LocationPermissionHelper(
        onPermissionGranted = {
            val cameraPositionState = rememberCameraPositionState {
                position = CameraPosition.fromLatLngZoom(LatLng(41.0082, 28.9784), 10f)
            }

            LaunchedEffect(Unit) {
                viewModel.fetchCurrentLocation()
            }

            // Camera update for current location or route
            LaunchedEffect(uiState.currentLocation, uiState.polylinePoints) {
                if (uiState.polylinePoints.isNotEmpty()) {
                    val builder = LatLngBounds.Builder()
                    uiState.polylinePoints.forEach { builder.include(it) }
                    val bounds = builder.build()
                    cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 100))
                } else {
                    uiState.currentLocation?.let {
                        cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(it, 15f))
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    properties = MapProperties(isMyLocationEnabled = true),
                    uiSettings = MapUiSettings(myLocationButtonEnabled = true)
                ) {
                    // Pickup Marker
                    uiState.currentLocation?.let {
                        Marker(
                            state = MarkerState(position = it),
                            title = "Pickup Location",
                            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
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
                                title = "Destination"
                            )
                        }
                    }
                }

                // Overlay UI
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .statusBarsPadding()
                ) {
                    // Search Bar
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

                // Logout Button
                IconButton(
                    onClick = {
                        viewModel.signOut()
                        onLogout()
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 80.dp, end = 16.dp) // Adjusted to not overlap with search
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
                            Text(
                                text = "Ride Status: ${status.replaceFirstChar { it.uppercase() }}",
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (status == "pending") {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
                                Text(
                                    "Finding a driver...",
                                    modifier = Modifier.padding(top = 8.dp),
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
