package com.erayoz.uberapp.ui.driver

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.erayoz.uberapp.service.LocationService
import com.erayoz.uberapp.ui.common.LocationPermissionHelper
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*

@Composable
fun DriverMapScreen(
    onLogout: () -> Unit,
    viewModel: DriverViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LocationPermissionHelper(
        onPermissionGranted = {
            val cameraPositionState = rememberCameraPositionState {
                position = CameraPosition.fromLatLngZoom(LatLng(0.0, 0.0), 15f)
            }

            LaunchedEffect(uiState.currentLocation) {
                uiState.currentLocation?.let {
                    cameraPositionState.animate(CameraUpdateFactory.newLatLng(it))
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    properties = MapProperties(isMyLocationEnabled = true),
                    uiSettings = MapUiSettings(myLocationButtonEnabled = true)
                ) {
                    uiState.currentLocation?.let {
                        Marker(
                            state = MarkerState(position = it),
                            title = "You",
                            snippet = "Current Location"
                        )
                    }
                }

                IconButton(
                    onClick = {
                        // Stop tracking before logout
                        if (uiState.isTracking) {
                            val stopIntent = Intent(context, LocationService::class.java).apply {
                                action = LocationService.ACTION_STOP
                            }
                            context.startService(stopIntent)
                        }
                        viewModel.signOut()
                        onLogout()
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .statusBarsPadding(),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Logout")
                }

                FloatingActionButton(
                    onClick = {
                        val isOnline = !uiState.isTracking
                        viewModel.setTracking(isOnline)
                        
                        val intent = Intent(context, LocationService::class.java).apply {
                            if (isOnline) {
                                putExtra("driverId", viewModel.driverId)
                            } else {
                                action = LocationService.ACTION_STOP
                            }
                        }
                        
                        if (isOnline) {
                            context.startForegroundService(intent)
                        } else {
                            context.startService(intent)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp)
                ) {
                    Text(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        text = if (uiState.isTracking) "Go Offline" else "Go Online"
                    )
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
