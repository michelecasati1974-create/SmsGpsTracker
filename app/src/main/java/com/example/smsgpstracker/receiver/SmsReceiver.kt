package com.example.smsgpstracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage
import android.util.Log

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.e("SmsReceiver", "onReceive chiamato, action=${intent.action}")

        if (intent.action == "android.provider.Telephony.SMS_RECEIVED") {

            val bundle = intent.extras ?: return
            val pdus = bundle["pdus"] as? Array<*> ?: return

            for (pdu in pdus) {
                val sms = SmsMessage.createFromPdu(pdu as ByteArray)
                val sender = sms.originatingAddress
                val message = sms.messageBody

                Log.d("SmsReceiver", "SMS ricevuto da $sender : $message")
            }
        }
    }
}


