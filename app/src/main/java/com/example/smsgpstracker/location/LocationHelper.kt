package com.example.smsgpstracker.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.*

class LocationHelper(context: Context) {

    private val fusedLocationClient =
        LocationServices.getFusedLocationProviderClient(context)

    /**
     * Ottiene la posizione corrente (GPS o network)
     */
    @SuppressLint("MissingPermission")
    fun getCurrentLocation(
        onSuccess: (Location) -> Unit,
        onError: () -> Unit
    ) {
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1000
        ).setMaxUpdates(1).build()

        fusedLocationClient.requestLocationUpdates(
            request,
            object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    fusedLocationClient.removeLocationUpdates(this)

                    val location = result.lastLocation
                    if (location != null) {
                        onSuccess(location)
                    } else {
                        onError()
                    }
                }
            },
            null
        )
    }
}


