package com.example.smsgpstracker.repository

import com.example.smsgpstracker.model.GpsTrackPoint
import java.util.concurrent.CopyOnWriteArrayList

object GpsTrackRepository {

    private val trackPoints = CopyOnWriteArrayList<GpsTrackPoint>()

    fun addPoint(point: GpsTrackPoint) {
        trackPoints.add(point)
    }

    fun getAllPoints(): List<GpsTrackPoint> {
        return trackPoints.toList()
    }

    fun lastPoint(): GpsTrackPoint? {
        return trackPoints.lastOrNull()
    }

    fun size(): Int {
        return trackPoints.size
    }

    fun clear() {
        trackPoints.clear()
    }
}
