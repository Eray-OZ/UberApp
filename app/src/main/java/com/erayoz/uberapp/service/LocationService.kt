package com.erayoz.uberapp.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.erayoz.uberapp.R
import com.erayoz.uberapp.UberApp
import com.erayoz.uberapp.data.model.DriverLocation
import com.erayoz.uberapp.data.repository.LocationRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@AndroidEntryPoint
class LocationService : Service() {

    @Inject
    lateinit var locationRepository: LocationRepository

    @Inject
    lateinit var fusedLocationClient: FusedLocationProviderClient

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var driverId: String? = null
    private var locationJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        driverId = intent?.getStringExtra("driverId")

        // Her durumda servisi bir foreground bildirimiyle başlatmak çökmeleri önler
        showNotification()

        if (action == ACTION_STOP || driverId == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        startLocationUpdates()
        return START_STICKY
    }

    private fun showNotification() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startLocationUpdates() {
        locationJob?.cancel()
        locationJob = serviceScope.launch {
            while (isActive) {
                fetchAndPublishLocation()
                delay(4000)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun fetchAndPublishLocation() {
        val id = driverId ?: return
        val cts = CancellationTokenSource()
        try {
            val location = fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cts.token
            ).await()

            location?.let {
                val driverLocation = DriverLocation(
                    driverId = id,
                    latitude = it.latitude,
                    longitude = it.longitude,
                    timestamp = System.currentTimeMillis()
                )
                locationRepository.updateDriverLocation(driverLocation)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            cts.cancel()
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, UberApp.LOCATION_CHANNEL_ID)
            .setContentTitle("UberApp")
            .setContentText("Konum takibi aktif")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        locationJob?.cancel()
        serviceScope.cancel()
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "STOP_LOCATION_SERVICE"
    }
}
