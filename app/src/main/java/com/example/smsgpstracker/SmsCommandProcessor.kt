package com.example.smsgpstracker

import android.content.Context
import android.content.Intent
import android.util.Log
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
            NotificationHelper.showNotification(
                context,
                "SMS ignorato",
                "Numero non autorizzato: $sender"
            )
            return
        }

        val msg = message.trim().uppercase()
        val command = msg.substringBefore(":")

        when (command) {

            "GPS" -> {
                Log.d("RX_CMD", "Comando GPS ricevuto")

                val data = msg.substringAfter("GPS").replace(":", "")
                val parts = data.split(",")

                if (parts.size != 2) return

                val lat = parts[0].toDoubleOrNull() ?: return
                val lon = parts[1].toDoubleOrNull() ?: return

                // 🔴 BROADCAST STANDARD
                val intent = Intent("com.example.smsgpstracker.GPS_POINT")
                intent.putExtra("lat", lat)
                intent.putExtra("lon", lon)
                intent.putExtra("time", System.currentTimeMillis())

                context.sendBroadcast(intent)

                SmsSender.sendSms(sender, "GPS ricevuto")
            }

            "STATUS" -> {
                SmsSender.sendSms(sender, "SmsGpsTracker attivo")
            }

            "PING" -> {
                SmsSender.sendSms(sender, "PONG")
            }
        }
    }
}

