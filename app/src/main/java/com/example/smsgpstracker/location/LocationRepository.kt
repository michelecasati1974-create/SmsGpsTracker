package com.example.smsgpstracker.location

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.*

class LocationRepository(context: Context) {

    private val fusedLocationClient =
        LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    fun getCurrentLocation(callback: (android.location.Location?) -> Unit) {
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                callback(location)
            }
            .addOnFailureListener {
                callback(null)
            }
    }
}



