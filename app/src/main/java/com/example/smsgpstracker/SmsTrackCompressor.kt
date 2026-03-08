package com.example.smsgpstracker

import com.google.android.gms.maps.model.LatLng
import kotlin.math.roundToInt

object SmsTrackCompressor {

    private const val SCALE = 100000
    private const val MAX_SMS_LENGTH = 150

    fun compress(points: List<LatLng>, seq: Int): String {

        if (points.isEmpty()) return ""

        val builder = StringBuilder()

        val first = points[0]

        builder.append("T#")
        builder.append(seq)
        builder.append("|")
        builder.append("%.5f".format(first.latitude))
        builder.append(",")
        builder.append("%.5f".format(first.longitude))

        var prevLat = first.latitude
        var prevLon = first.longitude

        for (i in 1 until points.size) {

            val p = points[i]

            val dLat = ((p.latitude - prevLat) * SCALE).roundToInt()
            val dLon = ((p.longitude - prevLon) * SCALE).roundToInt()

            val chunk = "|$dLat,$dLon"

            // evita superamento lunghezza SMS
            if (builder.length + chunk.length > MAX_SMS_LENGTH) {
                break
            }

            builder.append(chunk)

            prevLat = p.latitude
            prevLon = p.longitude
        }

        return builder.toString()
    }
}