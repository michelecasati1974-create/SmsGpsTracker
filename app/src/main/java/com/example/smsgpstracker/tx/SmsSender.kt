package com.example.smsgpstracker.tx

import android.telephony.SmsManager
import android.util.Log

object SmsSender {

    fun sendSms(phoneNumber: String, message: String) {
        try {
            val smsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            Log.d("TX_SMS", "SMS inviato a $phoneNumber")
        } catch (e: Exception) {
            Log.e("TX_SMS", "Errore invio SMS", e)
        }
    }
}
