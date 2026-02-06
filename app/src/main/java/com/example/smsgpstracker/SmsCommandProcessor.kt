package com.example.smsgpstracker

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.smsgpstracker.model.GpsTrackPoint
import com.example.smsgpstracker.repository.GpsTrackRepository

object SmsCommandProcessor {

    const val ACTION_NEW_POSITION =
        "com.example.smsgpstracker.ACTION_NEW_POSITION"

    fun process(context: Context, sender: String, body: String) {

        val text = body.trim().uppercase()

        val regex =
            Regex("""GPS[:\s]+(-?\d+(\.\d+)?),\s*(-?\d+(\.\d+)?)""")

        val match = regex.find(text) ?: return

        val latitude = match.groupValues[1].toDouble()
        val longitude = match.groupValues[3].toDouble()

        Log.d("SMS_RX", "Coordinate ricevute: $latitude , $longitude")

        val point = GpsTrackPoint(
            sender = sender,
            latitude = latitude,
            longitude = longitude,
            timestamp = System.currentTimeMillis()
        )

        GpsTrackRepository.addPoint(point)

        val intent = Intent(ACTION_NEW_POSITION).apply {
            setPackage(context.packageName)
        }

        context.sendBroadcast(intent)
    }
}





