package com.example.smsgpstracker

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.smsgpstracker.repository.GpsTrackRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object SmsCommandProcessor {

    const val ACTION_NEW_POSITION =
        "com.example.smsgpstracker.NEW_POSITION"

    fun process(context: Context, sender: String, body: String) {

        val text = body.trim().uppercase()

        val regex =
            Regex("""GPS[:\s]+(-?\d+(\.\d+)?),\s*(-?\d+(\.\d+)?)""")

        val match = regex.find(text)
        if (match == null) {
            Log.d("RX_SMS", "Formato GPS non valido")
            return
        }

        val lat = match.groupValues[1].toDouble()
        val lon = match.groupValues[3].toDouble()

        Log.d("RX_SMS", "GPS ricevuto: $lat , $lon")

        CoroutineScope(Dispatchers.IO).launch {

            // 💾 SALVATAGGIO DB
            GpsTrackRepository.addPoint(
                context = context,
                lat = lat,
                lon = lon
            )

            // 📢 NOTIFICA UI
            val intent = Intent(ACTION_NEW_POSITION)
            intent.putExtra("lat", lat)
            intent.putExtra("lon", lon)
            context.sendBroadcast(intent)
        }
    }
}






