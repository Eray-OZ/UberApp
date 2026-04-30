package com.erayoz.uberapp.ui.driver

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.erayoz.uberapp.service.LocationService
import com.erayoz.uberapp.ui.common.LocationPermissionHelper
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.PolyUtil
import com.google.maps.android.compose.*

@Composable
fun DriverMapScreen(
    onLogout: () -> Unit,
    viewModel: DriverViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pendingRides by viewModel.pendingRides.collectAsStateWithLifecycle()
    val activeRide by viewModel.activeRide.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LocationPermissionHelper(
        onPermissionGranted = {
            var hasCenteredOnLocation by remember { mutableStateOf(false) }
            val cameraPositionState = rememberCameraPositionState {
                position = CameraPosition.fromLatLngZoom(
                    uiState.currentLocation ?: LatLng(41.0082, 28.9784), 
                    if (uiState.currentLocation != null) 15f else 10f
                )
            }

            LaunchedEffect(uiState.currentLocation) {
                if (uiState.currentLocation != null && !hasCenteredOnLocation) {
                    cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(uiState.currentLocation!!, 15f))
                    hasCenteredOnLocation = true
                }
            }

            val pagerState = rememberPagerState(pageCount = { pendingRides.size })

            // Camera updates
            LaunchedEffect(uiState.currentLocation, uiState.routeToPickup, activeRide, pendingRides, pagerState.currentPage) {
                val builder = LatLngBounds.Builder()
                var hasPoints = false

                if (activeRide != null) {
                    val ride = activeRide!!
                    if (ride.status == "accepted") {
                        // Phase 1: Focus on Driver -> Pickup
                        uiState.currentLocation?.let { builder.include(it); hasPoints = true }
                        builder.include(LatLng(ride.pickupLatitude, ride.pickupLongitude)); hasPoints = true
                        uiState.routeToPickup.forEach { builder.include(it); hasPoints = true }
                    } else if (ride.status == "ongoing") {
                        // Phase 2: Focus on Driver -> Destination
                        uiState.currentLocation?.let { builder.include(it); hasPoints = true }
                        builder.include(LatLng(ride.destinationLatitude, ride.destinationLongitude)); hasPoints = true
                        val points = PolyUtil.decode(ride.polylinePoints)
                        points.forEach { builder.include(it); hasPoints = true }
                    }
                } else if (pendingRides.isNotEmpty()) {
                    // Phase: Previewing requests - Show ALL pending pickups
                    pendingRides.forEach { ride ->
                        builder.include(LatLng(ride.pickupLatitude, ride.pickupLongitude))
                        hasPoints = true
                    }
                    // Also include the destination of the CURRENTLY selected one
                    if (pagerState.currentPage < pendingRides.size) {
                        val selectedRide = pendingRides[pagerState.currentPage]
                        PolyUtil.decode(selectedRide.polylinePoints).forEach { builder.include(it) }
                    }
                } else {
                    // Just tracking
                    uiState.currentLocation?.let {
                        cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(it, 15f))
                        return@LaunchedEffect
                    }
                }

                if (hasPoints) {
                    cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(builder.build(), 150))
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    properties = MapProperties(isMyLocationEnabled = true),
                    uiSettings = MapUiSettings(myLocationButtonEnabled = true)
                ) {
                    val personIcon = remember(context) { com.erayoz.uberapp.util.BitmapUtils.bitmapDescriptorFromVector(context, com.erayoz.uberapp.R.drawable.ic_person) }
                    val carIcon = remember(context) { com.erayoz.uberapp.util.BitmapUtils.bitmapDescriptorFromVector(context, com.erayoz.uberapp.R.drawable.ic_car) }
                    val destIcon = remember(context) { com.erayoz.uberapp.util.BitmapUtils.bitmapDescriptorFromVector(context, com.erayoz.uberapp.R.drawable.ic_destination_pin) }

                    // Active Ride markers and routes
                    activeRide?.let { ride ->
                        if (ride.status == "accepted") {
                            // Phase 1: Navigation to Pickup
                            val pickup = LatLng(ride.pickupLatitude, ride.pickupLongitude)
                            Marker(
                                state = MarkerState(position = pickup),
                                title = "Passenger (Pickup)",
                                icon = personIcon
                            )
                            if (uiState.routeToPickup.isNotEmpty()) {
                                Polyline(points = uiState.routeToPickup, color = Color.Magenta, width = 12f)
                            }
                        } else if (ride.status == "ongoing") {
                            // Phase 2: Navigation to Destination
                            val dest = LatLng(ride.destinationLatitude, ride.destinationLongitude)
                            val points = PolyUtil.decode(ride.polylinePoints)
                            Marker(
                                state = MarkerState(position = dest),
                                title = "Destination",
                                icon = destIcon
                            )
                            Polyline(points = points, color = Color.Blue, width = 12f)
                        }
                    }

                    // Previews for ALL pending rides
                    if (activeRide == null && pendingRides.isNotEmpty() && uiState.isTracking) {
                        pendingRides.forEachIndexed { index, ride ->
                            val isSelected = index == pagerState.currentPage
                            val pickup = LatLng(ride.pickupLatitude, ride.pickupLongitude)
                            
                            // Show pickup for every ride
                            Marker(
                                state = MarkerState(position = pickup),
                                title = if (isSelected) "Selected Request" else "Ride Request",
                                icon = personIcon,
                                alpha = if (isSelected) 1.0f else 0.4f
                            )

                            // Show route and destination ONLY for the selected ride
                            if (isSelected) {
                                val dest = LatLng(ride.destinationLatitude, ride.destinationLongitude)
                                val points = PolyUtil.decode(ride.polylinePoints)
                                
                                Marker(
                                    state = MarkerState(position = dest), 
                                    title = "Destination", 
                                    alpha = 0.8f,
                                    icon = destIcon
                                )
                                Polyline(
                                    points = points, 
                                    color = Color.Blue.copy(alpha = 0.4f), 
                                    width = 10f
                                )
                            }
                        }
                    }

                    // Driver's own marker
                    uiState.currentLocation?.let {
                        Marker(
                            state = MarkerState(position = it),
                            title = "You (Driver)",
                            icon = carIcon
                        )
                    }
                }

                // Top Controls
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = { viewModel.signOut(); onLogout() },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Logout")
                    }

                    Button(
                        onClick = { viewModel.toggleTracking(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = if (uiState.isTracking) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    ) {
                        Text(if (uiState.isTracking) "Go Offline" else "Go Online")
                    }
                }

                // New Ride Request Overlay
                if (pendingRides.isNotEmpty() && activeRide == null && uiState.isTracking) {
                    Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 16.dp)) {
                        if (pendingRides.size > 1) {
                            Text(
                                text = "${pagerState.currentPage + 1} / ${pendingRides.size} Requests",
                                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 32.dp),
                            pageSpacing = 16.dp
                        ) { pageIndex ->
                            val ride = pendingRides[pageIndex]
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("New Ride Request!", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                                    if (ride.destinationAddress.isNotEmpty()) Text("To: ${ride.destinationAddress}")
                                    Text("Distance: ${ride.distanceText} (${ride.durationText})")
                                    Text("Price: ₺${"%.2f".format(ride.price)}")
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(onClick = { viewModel.acceptRide(ride) }, modifier = Modifier.fillMaxWidth()) {
                                        Text("Accept Ride")
                                    }
                                }
                            }
                        }
                    }
                }

                // Active Ride Controls
                activeRide?.let { ride ->
                    Card(
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(if (ride.status == "accepted") "Passenger Waiting" else "Trip Ongoing", fontWeight = FontWeight.Bold)
                            if (ride.status == "ongoing") Text("To: ${ride.destinationAddress}")
                            
                            if (uiState.activeDistance.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Text("Distance: ${uiState.activeDistance}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                    Text("Duration: ${uiState.activeDuration}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            Text("Durum: ${ride.status.replaceFirstChar { it.uppercase() }}")
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            when (ride.status) {
                                "accepted" -> {
                                    Button(onClick = { viewModel.updateRideStatus("ongoing") }, modifier = Modifier.fillMaxWidth()) {
                                        Text("Picked Up (Start Trip)")
                                    }
                                }
                                "ongoing" -> {
                                    Button(onClick = { viewModel.updateRideStatus("completed") }, modifier = Modifier.fillMaxWidth()) {
                                        Text("Complete Trip")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        onPermissionDenied = {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Location permission is required to use this screen.")
            }
        }
    )
}
