package com.example.smsgpstracker.receiver

import android.content.Context
import android.util.Log
import com.example.smsgpstracker.tx.SmsSender
import com.example.smsgpstracker.tx.GpsHelper

object SmsCommandProcessor {

    private val authorizedNumbers = arrayOf(
        "+393394983827", // Tuo numero
        "+393486933859"  // Altro numero autorizzato
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
            return
        }

        val msg = message.trim().uppercase()

        // Controllo PIN se presente
        if (msg.contains(":")) {
            val parts = msg.split(":")
            if (parts.size == 2 && parts[1] != COMMAND_PIN) {
                Log.d("RX_CMD", "PIN errato da $sender")
                return
            }
        }

        val command = msg.split(":")[0]

        when (command) {
            "GPS" -> {
                Log.d("RX_CMD", "Comando GPS ricevuto da $sender")

                // Ottieni posizione reale e invia SMS
                GpsHelper.getCurrentLocation { lat, lon ->
                    val gpsMsg = if (lat != 0.0 && lon != 0.0)
                        "Posizione: lat $lat, lon $lon"
                    else
                        "Impossibile recuperare posizione"

                    SmsSender.sendSms(sender, gpsMsg)
                }
            }

            "STATUS" -> {
                Log.d("RX_CMD", "Comando STATUS ricevuto da $sender")
                SmsSender.sendSms(sender, "SmsGpsTracker attivo e funzionante")
            }

            "PING" -> {
                Log.d("RX_CMD", "Comando PING ricevuto da $sender")
                SmsSender.sendSms(sender, "PONG")
            }

            else -> {
                Log.d("RX_CMD", "Comando sconosciuto da $sender")
            }
        }
    }
}



