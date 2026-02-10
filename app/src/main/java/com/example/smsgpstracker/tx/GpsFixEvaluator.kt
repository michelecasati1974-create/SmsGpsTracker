package com.example.smsgpstracker

import android.location.Location

object GpsFixEvaluator {

    const val MAX_ACCURACY_METERS = 25f
    const val MAX_LOCATION_AGE_MS = 30_000L

    fun isValid(location: Location?): Boolean {
        if (location == null) return false
        if (!location.hasAccuracy()) return false
        if (location.accuracy > MAX_ACCURACY_METERS) return false
        if (System.currentTimeMillis() - location.time > MAX_LOCATION_AGE_MS) return false
        return true
    }
}