package com.example.smsgpstracker.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "gps_track")
data class GpsTrackPoint(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long
)