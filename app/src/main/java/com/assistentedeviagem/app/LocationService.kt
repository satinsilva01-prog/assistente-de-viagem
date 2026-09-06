package com.assistentedeviagem.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import com.google.android.gms.location.*

class LocationService : Service() {

    private lateinit var client: FusedLocationProviderClient
    private lateinit var callback: LocationCallback
    private var lastLocation: Location? = null

    override fun onCreate() {
        super.onCreate()

        val prefs = getSharedPreferences("trip", 0)
        prefs.edit().putBoolean("running", true).apply()

        val channel = NotificationChannel(
            "trip",
            "Assistente de Viagem",
            NotificationManager.IMPORTANCE_LOW
        )

        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)

        val notification = Notification.Builder(this, "trip")
            .setContentTitle("Assistente de Viagem")
            .setContentText("Viagem em andamento • GPS ativo")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                10,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(10, notification)
        }

        client = LocationServices.getFusedLocationProviderClient(this)

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            2000L
        )
            .setMinUpdateIntervalMillis(1000L)
            .setMinUpdateDistanceMeters(1f)
            .build()

        callback = object : LocationCallback() {

            override fun onLocationResult(result: LocationResult) {

                val location = result.lastLocation ?: return

                val p = getSharedPreferences("trip", 0)

                var distance = p
                    .getFloat("distance_km", 0f)
                    .toDouble()

                val previous = lastLocation

                if (previous != null) {

                    val distanceMeters = FloatArray(1)

                    Location.distanceBetween(
                        previous.latitude,
                        previous.longitude,
                        location.latitude,
                        location.longitude,
                        distanceMeters
                    )

                    if (
                        distanceMeters[0] > 0.5f &&
                        distanceMeters[0] < 2000f
                    ) {
                        distance += distanceMeters[0] / 1000.0
                    }
                }

                lastLocation = location

                val speedKmh =
                    if (location.hasSpeed()) {
                        location.speed * 3.6f
                    } else {
                        0f
                    }

                p.edit()
                    .putString("lat", location.latitude.toString())
                    .putString("lon", location.longitude.toString())
                    .putFloat("speed", speedKmh)
                    .putFloat("accuracy", location.accuracy)
                    .putFloat("distance_km", distance.toFloat())
                    .apply()
            }
        }

        if (
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
        ) {
            client.requestLocationUpdates(
                request,
                callback,
                Looper.getMainLooper()
            )
        }
    }

    override fun onDestroy() {

        getSharedPreferences("trip", 0)
            .edit()
            .putBoolean("running", false)
            .apply()

        if (::client.isInitialized) {
            client.removeLocationUpdates(callback)
        }

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
