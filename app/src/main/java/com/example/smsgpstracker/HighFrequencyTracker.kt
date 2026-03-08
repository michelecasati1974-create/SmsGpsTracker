package com.example.smsgpstracker

import android.location.Location
import kotlin.math.abs

class HighFrequencyTracker {

    private var lastAcceptedLocation: Location? = null

    private val minDistanceMeters = 2.0f

    fun shouldAccept(location: Location): Boolean {

        val last = lastAcceptedLocation

        if (last == null) {
            lastAcceptedLocation = location
            return true
        }

        val distance = last.distanceTo(location)

        if (distance < minDistanceMeters) {
            return false
        }

        lastAcceptedLocation = location
        return true
    }

    fun reset() {
        lastAcceptedLocation = null
    }
}