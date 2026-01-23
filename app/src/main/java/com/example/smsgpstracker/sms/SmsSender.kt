package com.example.smsgpstracker.sms

import android.content.Context
import android.telephony.SmsManager
import android.util.Log

object SmsSender {

    fun send(context: Context, phone: String, message: String) {
        try {
            SmsManager.getDefault()
                .sendTextMessage(phone, null, message, null, null)
            Log.d("SmsSender", "SMS inviato a $phone : $message")
        } catch (e: Exception) {
            Log.e("SmsSender", "Errore invio SMS", e)
        }
    }
}


