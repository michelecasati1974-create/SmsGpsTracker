package com.example.smsgpstracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage
import android.util.Log
import com.example.smsgpstracker.location.LocationRepository
import android.telephony.SmsManager

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        if (intent.action != "android.provider.Telephony.SMS_RECEIVED") return

        val bundle = intent.extras ?: return
        val pdus = bundle["pdus"] as? Array<*> ?: return

        for (pdu in pdus) {
            val sms = SmsMessage.createFromPdu(pdu as ByteArray)
            val sender = sms.originatingAddress ?: return
            val message = sms.messageBody.trim().uppercase()

            Log.d("SmsReceiver", "SMS da $sender : $message")

            if (message == "GPS?") {
                val repo = LocationRepository(context)

                repo.getLastLocation { location ->
                    val response = if (location != null) {
                        "LAT=${location.latitude}\nLON=${location.longitude}"
                    } else {
                        "GPS NON DISPONIBILE"
                    }

                    SmsManager.getDefault()
                        .sendTextMessage(sender, null, response, null, null)

                    Log.i("SmsReceiver", "Risposta GPS inviata")
                }
            }
        }
    }
}



