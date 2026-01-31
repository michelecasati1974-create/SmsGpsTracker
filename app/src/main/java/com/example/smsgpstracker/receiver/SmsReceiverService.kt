package com.example.smsgpstracker.receiver

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.telephony.SmsMessage
import android.util.Log

class SmsReceiverService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null && intent.extras != null) {
            val bundle = intent.extras
            try {
                val pdus = bundle?.get("pdus") as Array<*>
                for (pdu in pdus) {
                    val sms = SmsMessage.createFromPdu(pdu as ByteArray)
                    val sender = sms.displayOriginatingAddress
                    val messageBody = sms.messageBody

                    Log.d("RX_SMS_SERVICE", "Da: $sender - Msg: $messageBody")

                    // Processa il comando SMS
                    SmsCommandProcessor.process(this, sender, messageBody)
                }
            } catch (e: Exception) {
                Log.e("RX_SMS_SERVICE", "Errore ricezione SMS", e)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        // Non è previsto binding
        return null
    }
}
