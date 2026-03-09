package com.example.smsgpstracker

import android.util.Log
import kotlin.math.roundToInt
import com.example.smsgpstracker.tx.PolylineCodec

class RxTrackAssembler {

    private var expectedSeq = 0

    fun processSms(payload: String): List<Pair<Double, Double>>? {

        if (!payload.startsWith("T#")) return null

        val parts = payload.split("|")

        if (parts.size < 2) return null

        val seq = parts[0].substringAfter("#").toInt()

        val encodedPolyline = parts[1]

        val points = PolylineCodec.decode(encodedPolyline)

        Log.d("RX_TRACK", "decoded points=${points.size}")

        return points
    }
}