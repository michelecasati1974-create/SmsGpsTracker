package com.example.smsgpstracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage
import android.util.Log
import com.example.smsgpstracker.RxActivity

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        if (intent.action != "android.provider.Telephony.SMS_RECEIVED") return

        val bundle = intent.extras ?: return
        val pdus = bundle["pdus"] as? Array<*> ?: return

        for (pdu in pdus) {
            val sms = SmsMessage.createFromPdu(pdu as ByteArray)
            val sender = sms.originatingAddress ?: continue
            val message = sms.messageBody ?: ""

            Log.d("SmsReceiver", "SMS ricevuto: $sender -> $message")

            if (message.trim().uppercase() == "POS") {

                val launchIntent = Intent(context, RxActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra("sender", sender)
                    putExtra("message", message)
                    putExtra("time", System.currentTimeMillis())
                }

                context.startActivity(launchIntent)
            }
        }
    }
}
