package com.example.smsgpstracker

import com.google.android.gms.maps.model.LatLng

object PositionRepository {

    private val positions = mutableListOf<LatLng>()

    fun add(lat: Double, lon: Double) {
        positions.add(LatLng(lat, lon))
    }

    fun getAll(): List<LatLng> = positions.toList()

    fun count(): Int = positions.size

    fun last(): LatLng? = positions.lastOrNull()
}
