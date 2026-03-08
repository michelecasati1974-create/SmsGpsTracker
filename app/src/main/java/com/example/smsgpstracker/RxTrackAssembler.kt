package com.example.smsgpstracker

import android.util.Log
import kotlin.math.roundToInt

class RxTrackAssembler {

    private var expectedSeq = 0

    fun processSms(payload: String): List<Pair<Double, Double>>? {

        if (!payload.startsWith("T#")) return null

        val parts = payload.split("|")
        if (parts.size < 2) return null

        val seqPart = parts[0]
        val seq = seqPart.substringAfter("#").toInt()

        if (seq != expectedSeq) {
            Log.w("RX_TRACK", "Sequence mismatch expected=$expectedSeq got=$seq")
            expectedSeq = seq
        }

        expectedSeq++

        val baseCoords = parts[1].split(",")

        var lat = baseCoords[0].toDouble()
        var lon = baseCoords[1].toDouble()

        val result = mutableListOf<Pair<Double, Double>>()

        result.add(Pair(lat, lon))

        for (i in 2 until parts.size) {

            val d = parts[i].split(",")

            val dLat = d[0].toInt()
            val dLon = d[1].toInt()

            lat += dLat * 0.00001
            lon += dLon * 0.00001

            lat = ((lat * 100000.0).roundToInt()) / 100000.0
            lon = ((lon * 100000.0).roundToInt()) / 100000.0

            result.add(Pair(lat, lon))
        }

        Log.d("RX_TRACK", "points reconstructed=${result.size}")

        return result
    }
}