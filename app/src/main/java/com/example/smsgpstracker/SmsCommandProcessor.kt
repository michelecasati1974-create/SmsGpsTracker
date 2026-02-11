package com.example.smsgpstracker

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.smsgpstracker.repository.GpsTrackRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object SmsCommandProcessor {

    const val ACTION_SMS_EVENT =
        "com.example.smsgpstracker.SMS_EVENT"

    fun process(context: Context, sender: String, body: String) {

        val text = body.trim()

        Log.d("RX_SMS", "SMS ricevuto: $text")

        // =====================================================
        // 📌 CTRL COMMANDS
        // =====================================================

        if (text.equals("CTRL:START", true) ||
            text.equals("CTRL:END", true) ||
            text.equals("CTRL:STOP", true)
        ) {

            val intent = Intent(ACTION_SMS_EVENT)
            intent.putExtra("SMS_BODY", text.uppercase())
            context.sendBroadcast(intent)

            Log.d("RX_SMS", "CTRL broadcast inviato: $text")
            return
        }

        // =====================================================
        // 📌 GPS MESSAGE
        // =====================================================

        val regex =
            Regex("""GPS[:\s]+(-?\d+(\.\d+)?),\s*(-?\d+(\.\d+)?)""")

        val match = regex.find(text.uppercase())

        if (match == null) {
            Log.d("RX_SMS", "Formato non riconosciuto")
            return
        }

        val lat = match.groupValues[1].toDouble()
        val lon = match.groupValues[3].toDouble()

        Log.d("RX_SMS", "GPS valido: $lat , $lon")

        CoroutineScope(Dispatchers.IO).launch {

            // 💾 Salva nel DB
            GpsTrackRepository.addPoint(
                context = context,
                lat = lat,
                lon = lon
            )

            // 📢 Notifica UI
            val intent = Intent(ACTION_SMS_EVENT)
            intent.putExtra("SMS_BODY", "GPS:$lat,$lon")
            context.sendBroadcast(intent)

            Log.d("RX_SMS", "Broadcast GPS inviato")
        }
    }
}






