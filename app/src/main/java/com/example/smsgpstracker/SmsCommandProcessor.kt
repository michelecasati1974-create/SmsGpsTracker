package com.example.smsgpstracker

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.smsgpstracker.repository.GpsTrackRepository
import kotlinx.coroutines.*
import android.telephony.SmsManager


object SmsCommandProcessor {

    const val ACTION_SMS_EVENT =
        "com.example.smsgpstracker.SMS_EVENT"

    fun process(context: Context, sender: String, body: String) {


        val text = body.trim()

        Log.d("RX_PROCESS", "SMS ricevuto: $text")

        // =========================
        // POSIZIONE MANUALE
        // =========================
        if (text.contains("POS MANUALE", ignoreCase = true)) {

            val coordsRegex = Regex("""(-?\d+(\.\d+)?),\s*(-?\d+(\.\d+)?)""")
            val match = coordsRegex.find(text)

            if (match != null) {

                val lat = match.groupValues[1].toDouble()
                val lon = match.groupValues[3].toDouble()

                sendInternalBroadcast(
                    context,
                    "GPS_MANUAL",
                    lat,
                    lon
                )
            }

            return
        }

        // =========================
        // COMANDI DI CONTROLLO
        // =========================
        val prefs = context.getSharedPreferences("HEARTBEAT", Context.MODE_PRIVATE)
        prefs.edit()
            .putLong("lastAlive", System.currentTimeMillis())
            .apply()

        if (text.equals("CTRL:PONG", true)) {

            val prefs = context.getSharedPreferences("HEARTBEAT", Context.MODE_PRIVATE)
            prefs.edit()
                .putLong("lastAlive", System.currentTimeMillis())
                .apply()

            return
        }

        if (text.equals("CTRL:PING", true)) {

            SmsManager.getDefault().sendTextMessage(
                sender,
                null,
                "CTRL:PONG",
                null,
                null
            )

            return
        }

        if (
            text.equals("CTRL:START", true) ||
            text.equals("CTRL:END", true) ||
            text.equals("CTRL:STOP", true)
        ) {

            sendInternalBroadcast(context, text)
            return
        }


        val regex =
            Regex("""GPS[:\s]+(-?\d+(\.\d+)?),\s*(-?\d+(\.\d+)?)""")

        val match = regex.find(text.uppercase())

        if (match != null) {

            val prefs = context.getSharedPreferences("HEARTBEAT", Context.MODE_PRIVATE)
            prefs.edit()
                .putLong("lastAlive", System.currentTimeMillis())
                .apply()

            val lat = match.groupValues[1].toDouble()
            val lon = match.groupValues[3].toDouble()

            // ✔ GPS valido → RX è vivo
            sendInternalBroadcast(context, "RX_ALIVE")

            CoroutineScope(Dispatchers.IO).launch {

                GpsTrackRepository.addPoint(
                    context,
                    lat,
                    lon
                )

                sendInternalBroadcast(
                    context,
                    "GPS",
                    lat,
                    lon
                )
            }
        }
    }




    // ======================================================
    // BROADCAST ESPLICITO (FIX ANDROID 13-15)
    // ======================================================
    private fun sendInternalBroadcast(
        context: Context,
        type: String,
        lat: Double? = null,
        lon: Double? = null
    ) {

        val appContext = context.applicationContext

        val intent = Intent(ACTION_SMS_EVENT).apply {

            setPackage(appContext.packageName)

            putExtra("SMS_BODY", type)

            if (lat != null && lon != null) {
                putExtra("lat", lat)
                putExtra("lon", lon)
            }

            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        }

        Log.d("RX_FLOW", "Broadcast interno inviato: $type")

        appContext.sendBroadcast(intent)
    }


}






