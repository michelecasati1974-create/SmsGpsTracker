package com.example.smsgpstracker.sms

import android.telephony.SmsManager

object SmsSender {

    fun sendSms(phoneNumber: String, message: String) {
        val smsManager = SmsManager.getDefault()
        smsManager.sendTextMessage(
            phoneNumber,
            null,
            message,
            null,
            null
        )
    }
}


