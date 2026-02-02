package com.example.smsgpstracker.model

data class GpsTrackPoint(
    val timestamp: Long,
    val sender: String,
    val latitude: Double,
    val longitude: Double
)