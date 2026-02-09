package com.example.smsgpstracker.repository

import android.content.Context
import com.example.smsgpstracker.db.AppDatabase
import com.example.smsgpstracker.model.GpsTrackPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object GpsTrackRepository {

    suspend fun addPoint(
        context: Context,
        lat: Double,
        lon: Double
    ) = withContext(Dispatchers.IO) {

        val point = GpsTrackPoint(
            latitude = lat,
            longitude = lon,
            timestamp = System.currentTimeMillis()
        )

        AppDatabase.get(context)
            .gpsTrackDao()
            .insert(point)
    }

    suspend fun getAll(context: Context): List<GpsTrackPoint> =
        withContext(Dispatchers.IO) {
            AppDatabase.get(context)
                .gpsTrackDao()
                .getAll()
        }

    suspend fun clear(context: Context) =
        withContext(Dispatchers.IO) {
            AppDatabase.get(context)
                .gpsTrackDao()
                .clear()
        }
}
