package com.example.smsgpstracker.receiver

import android.content.Context
import android.util.Log
import android.widget.Toast

object SmsCommandProcessor {

    fun process(context: Context, sender: String, message: String) {

        val command = message.trim().uppercase()

        when (command) {

            "GPS" -> {
                Log.d("RX_CMD", "Comando GPS ricevuto da $sender")
                Toast.makeText(context, "CMD GPS", Toast.LENGTH_SHORT).show()
                // STEP 5.5 → risposta SMS
            }

            "STATUS" -> {
                Log.d("RX_CMD", "Comando STATUS ricevuto")
                Toast.makeText(context, "CMD STATUS", Toast.LENGTH_SHORT).show()
            }

            "PING" -> {
                Log.d("RX_CMD", "Comando PING ricevuto")
                Toast.makeText(context, "CMD PING", Toast.LENGTH_SHORT).show()
            }

            else -> {
                Log.d("RX_CMD", "SMS ignorato")
            }
        }
    }
}

