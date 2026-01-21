package com.example.smsgpstracker.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices

class LocationRepository(context: Context) {

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    fun getLastLocation(
        onResult: (Location?) -> Unit
    ) {
        fusedClient.lastLocation
            .addOnSuccessListener { location ->
                onResult(location)
            }
            .addOnFailureListener {
                onResult(null)
            }
    }
}


