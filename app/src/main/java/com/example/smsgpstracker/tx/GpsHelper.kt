package com.example.smsgpstracker.tx

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.CancellationTokenSource

object GpsHelper {

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    fun init(context: Context) {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    }

    @SuppressLint("MissingPermission")
    fun getCurrentLocation(onResult: (lat: Double, lon: Double) -> Unit) {
        val cancellationToken = CancellationTokenSource()
        fusedLocationClient.getCurrentLocation(
            com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
            cancellationToken.token
        ).addOnSuccessListener { location ->
            if (location != null) {
                Log.d("GPS_HELPER", "Lat: ${location.latitude}, Lon: ${location.longitude}")
                onResult(location.latitude, location.longitude)
            } else {
                Log.e("GPS_HELPER", "Location null")
                onResult(0.0, 0.0)
            }
        }.addOnFailureListener {
            Log.e("GPS_HELPER", "Errore GPS", it)
            onResult(0.0, 0.0)
        }
    }
}
