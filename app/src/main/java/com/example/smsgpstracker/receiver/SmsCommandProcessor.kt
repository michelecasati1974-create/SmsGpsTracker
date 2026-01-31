package com.example.smsgpstracker.receiver

import android.content.Context
import android.util.Log
import com.example.smsgpstracker.tx.SmsSender

object SmsCommandProcessor {

    fun process(context: Context, sender: String, message: String) {

        if (!isAuthorized(sender)) {
            Log.d("RX_CMD", "Numero non autorizzato: $sender")
            return
        }

        val msg = message.trim().uppercase()

        // Controllo PIN facoltativo per comandi sensibili
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
                SmsSender.sendSms(sender, "GPS request ricevuto. Posizione in elaborazione...")
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
private val authorizedNumbers = arrayOf(
    "+393394983827", // Tuo numero
    "+393486933859" // Altro numero autorizzato
)
private fun isAuthorized(sender: String): Boolean {
    return authorizedNumbers.contains(sender)
}
private const val COMMAND_PIN = "1234"
private fun checkPin(message: String): Boolean {
    return message.contains(":$COMMAND_PIN")
}


