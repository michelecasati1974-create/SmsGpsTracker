package com.example.smsgpstracker.receiver

import android.content.Context
import android.util.Log
import com.example.smsgpstracker.tx.SmsSender

object SmsCommandProcessor {

    fun process(context: Context, sender: String, message: String) {

        val command = message.trim().uppercase()

        when (command) {

            "GPS" -> {
                Log.d("RX_CMD", "Comando GPS ricevuto")
                SmsSender.sendSms(
                    sender,
                    "GPS request ricevuto. Posizione in elaborazione..."
                )
            }

            "STATUS" -> {
                Log.d("RX_CMD", "Comando STATUS ricevuto")
                SmsSender.sendSms(
                    sender,
                    "SmsGpsTracker attivo e funzionante"
                )
            }

            "PING" -> {
                Log.d("RX_CMD", "Comando PING ricevuto")
                SmsSender.sendSms(
                    sender,
                    "PONG"
                )
            }

            else -> {
                Log.d("RX_CMD", "SMS ignorato")
            }
        }
    }
}


