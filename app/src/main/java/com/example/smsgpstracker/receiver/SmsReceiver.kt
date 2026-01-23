package com.example.smsgpstracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage
import android.util.Log
import com.example.smsgpstracker.service.GpsService
import androidx.core.content.ContextCompat

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        if (intent.action != "android.provider.Telephony.SMS_RECEIVED") return

        val bundle = intent.extras ?: return
        val pdus = bundle["pdus"] as? Array<*> ?: return

        for (pdu in pdus) {
            val sms = SmsMessage.createFromPdu(pdu as ByteArray)
            val sender = sms.originatingAddress ?: continue
            val message = sms.messageBody.trim().uppercase()

            Log.d("SmsReceiver", "SMS da $sender : $message")

            if (message == "GPS") {
                val serviceIntent = Intent(context, GpsService::class.java)
                serviceIntent.putExtra("phone", sender)
                ContextCompat.startForegroundService(context, serviceIntent)
            }
        }
    }
}
