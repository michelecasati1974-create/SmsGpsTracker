package com.example.smsgpstracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.telephony.SmsMessage
import android.util.Log

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val bundle: Bundle? = intent.extras
        try {
            val pdus = bundle?.get("pdus") as Array<*>
            for (pdu in pdus) {
                val sms = SmsMessage.createFromPdu(pdu as ByteArray)
                val sender = sms.displayOriginatingAddress
                val messageBody = sms.messageBody

                Log.d("RX_SMS", "Da: $sender - Msg: $messageBody")

                // Avvia il Service in background
                val serviceIntent = Intent(context, SmsReceiverService::class.java)
                serviceIntent.putExtra("pdus", arrayOf(pdu))
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e("RX_SMS", "Errore ricezione SMS", e)
        }
    }
}

