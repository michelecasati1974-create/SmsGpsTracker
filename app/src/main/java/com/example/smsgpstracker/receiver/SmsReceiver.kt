package com.example.smsgpstracker.receiver


import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.telephony.SmsMessage
import android.util.Log

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        if (intent.action != "android.provider.Telephony.SMS_RECEIVED") return

        val bundle: Bundle? = intent.extras
        if (bundle == null) return

        val pdus = bundle["pdus"] as Array<*>? ?: return

        for (pdu in pdus) {
            val sms = SmsMessage.createFromPdu(pdu as ByteArray)
            val sender = sms.displayOriginatingAddress
            val message = sms.messageBody

            Log.d("RX_SMS", "Da: $sender - Msg: $message")

            SmsCommandProcessor.process(context, sender, message)
        }
    }
}

