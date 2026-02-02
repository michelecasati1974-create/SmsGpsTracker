package com.example.smsgpstracker.receiver

import android.content.Context
import android.util.Log
import com.example.smsgpstracker.tx.SmsSender
import com.example.smsgpstracker.tx.GpsHelper
import com.example.smsgpstracker.tx.NotificationHelper
import com.example.smsgpstracker.model.GpsTrackPoint
import com.example.smsgpstracker.repository.GpsTrackRepository


object SmsCommandProcessor {

    private val authorizedNumbers = arrayOf(
        "+393394983827",
        "+393486933859"
    )

    private const val COMMAND_PIN = "1234"

    private fun isAuthorized(sender: String): Boolean {
        return authorizedNumbers.contains(sender)
    }

    private fun checkPin(message: String): Boolean {
        return message.contains(":$COMMAND_PIN")
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

                NotificationHelper.showNotification(
                    context,
                    "Comando GPS",
                    "Ricevuto da $sender"
                )

                GpsHelper.getCurrentLocation { lat, lon ->

                    val point = GpsTrackPoint(
                        timestamp = System.currentTimeMillis(),
                        sender = sender,
                        latitude = lat,
                        longitude = lon
                    )

                    // 🔴 SALVA PUNTO GPS
                    GpsTrackRepository.addPoint(point)

                    val gpsMsg = if (lat != 0.0 && lon != 0.0)
                        "Posizione: lat $lat, lon $lon"
                    else
                        "Impossibile recuperare posizione"

                    SmsSender.sendSms(sender, gpsMsg)

                    NotificationHelper.showNotification(
                        context,
                        "Risposta GPS inviata",
                        gpsMsg
                    )
                }
            }


            "STATUS" -> {
                Log.d("RX_CMD", "Comando STATUS ricevuto da $sender")
                SmsSender.sendSms(sender, "SmsGpsTracker attivo e funzionante")

                NotificationHelper.showNotification(
                    context,
                    "Comando STATUS",
                    "Risposta inviata a $sender"
                )
            }

            "PING" -> {
                Log.d("RX_CMD", "Comando PING ricevuto da $sender")
                SmsSender.sendSms(sender, "PONG")

                NotificationHelper.showNotification(
                    context,
                    "Comando PING",
                    "Risposta PONG inviata a $sender"
                )
            }

            else -> {
                Log.d("RX_CMD", "Comando sconosciuto da $sender")
                NotificationHelper.showNotification(
                    context,
                    "Comando sconosciuto",
                    "Ricevuto da $sender: $command"
                )
            }
        }
    }
}




