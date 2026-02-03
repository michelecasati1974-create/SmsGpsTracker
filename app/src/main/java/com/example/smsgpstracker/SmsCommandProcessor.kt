package com.example.smsgpstracker

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.smsgpstracker.tx.NotificationHelper
import com.example.smsgpstracker.tx.SmsSender

object SmsCommandProcessor {

    private val authorizedNumbers = arrayOf(
        "+393394983827",
        "+393486933859"
    )

    private const val COMMAND_PIN = "1234"

    private fun isAuthorized(sender: String): Boolean {
        return authorizedNumbers.contains(sender)
    }

    fun process(context: Context, sender: String, message: String) {

        if (!isAuthorized(sender)) {
            Log.d("RX_CMD", "Numero non autorizzato: $sender")
            NotificationHelper.showNotification(
                context,
                "SMS ignorato",
                "Numero non autorizzato: $sender"
            )
            return
        }

        val msg = message.trim().uppercase()

        if (msg.contains(":")) {
            val parts = msg.split(":")
            if (parts.size == 2 && parts[1] != COMMAND_PIN) {
                Log.d("RX_CMD", "PIN errato da $sender")
                NotificationHelper.showNotification(
                    context,
                    "PIN errato",
                    "Messaggio ricevuto da $sender con PIN errato"
                )
                return
            }
        }

        val command = msg.split(":")[0]

        when (command) {

            "GPS" -> {
                Log.d("RX_CMD", "Comando GPS ricevuto da $sender")

                val coords = msg.replace("GPS", "")
                    .replace(":", "")
                    .split(",")

                if (coords.size != 2) return

                val lat = coords[0].toDoubleOrNull() ?: return
                val lon = coords[1].toDoubleOrNull() ?: return

                val point = GpsPoint(lat, lon, System.currentTimeMillis())

                val intent = Intent("GPS_POINT_RECEIVED")
                intent.putExtra("lat", point.lat)
                intent.putExtra("lon", point.lon)
                intent.putExtra("time", point.timestamp)

                LocalBroadcastManager
                    .getInstance(context)
                    .sendBroadcast(intent)

                SmsSender.sendSms(sender, "GPS ricevuto")
            }

            "STATUS" -> {
                SmsSender.sendSms(sender, "SmsGpsTracker attivo")
            }

            "PING" -> {
                SmsSender.sendSms(sender, "PONG")
            }

            else -> {
                NotificationHelper.showNotification(
                    context,
                    "Comando sconosciuto",
                    "Ricevuto da $sender: $command"
                )
            }
        }
    }
}
