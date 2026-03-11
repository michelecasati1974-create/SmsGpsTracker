package com.example.smsgpstracker.rxmulti

object RxMultiTrackRepository {

    val points = mutableListOf<Pair<Double,Double>>()

    fun add(pointsNew: List<Pair<Double,Double>>) {

        points.addAll(pointsNew)
    }

    fun clear() {
        points.clear()
    }
}