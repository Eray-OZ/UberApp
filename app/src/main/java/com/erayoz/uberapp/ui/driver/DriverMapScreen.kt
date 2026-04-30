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
            val cameraPositionState = rememberCameraPositionState {
                position = CameraPosition.fromLatLngZoom(LatLng(41.0082, 28.9784), 10f)
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
                } else if (pendingRides.isNotEmpty() && pagerState.currentPage < pendingRides.size) {
                    // Previewing a request
                    val ride = pendingRides[pagerState.currentPage]
                    val points = PolyUtil.decode(ride.polylinePoints)
                    points.forEach { builder.include(it); hasPoints = true }
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
                    // Active Ride markers and routes
                    activeRide?.let { ride ->
                        if (ride.status == "accepted") {
                            // Phase 1: Navigation to Pickup
                            val pickup = LatLng(ride.pickupLatitude, ride.pickupLongitude)
                            Marker(
                                state = MarkerState(position = pickup),
                                title = "Yolcu Bekliyor",
                                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
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
                                title = "Varış Noktası",
                                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
                            )
                            Polyline(points = points, color = Color.Blue, width = 12f)
                        }
                    }

                    // Preview for pending rides
                    if (activeRide == null && pendingRides.isNotEmpty() && uiState.isTracking) {
                        val ride = pendingRides[pagerState.currentPage]
                        val pickup = LatLng(ride.pickupLatitude, ride.pickupLongitude)
                        val dest = LatLng(ride.destinationLatitude, ride.destinationLongitude)
                        val points = PolyUtil.decode(ride.polylinePoints)

                        Marker(state = MarkerState(position = pickup), title = "Alınacak Nokta", alpha = 0.6f)
                        Marker(state = MarkerState(position = dest), title = "Hedef", alpha = 0.6f)
                        Polyline(points = points, color = Color.Gray.copy(alpha = 0.5f), width = 10f)
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
                        onClick = {
                            val isOnline = !uiState.isTracking
                            viewModel.setTracking(isOnline)
                            val intent = Intent(context, LocationService::class.java).apply {
                                if (isOnline) putExtra("driverId", viewModel.driverId)
                                else action = LocationService.ACTION_STOP
                            }
                            if (isOnline) context.startForegroundService(intent)
                            else context.startService(intent)
                        },
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
                            Text(if (ride.status == "accepted") "Sizi Bekleyen Yolcu" else "Yolculuk Başladı", fontWeight = FontWeight.Bold)
                            if (ride.status == "ongoing") Text("Hedef: ${ride.destinationAddress}")
                            Text("Durum: ${ride.status.replaceFirstChar { it.uppercase() }}")
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            when (ride.status) {
                                "accepted" -> {
                                    Button(onClick = { viewModel.updateRideStatus("ongoing") }, modifier = Modifier.fillMaxWidth()) {
                                        Text("Yolcuyu Aldım (Start Trip)")
                                    }
                                }
                                "ongoing" -> {
                                    Button(onClick = { viewModel.updateRideStatus("completed") }, modifier = Modifier.fillMaxWidth()) {
                                        Text("Yolculuğu Tamamla")
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
