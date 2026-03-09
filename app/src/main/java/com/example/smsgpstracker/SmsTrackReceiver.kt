package com.example.smsgpstracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.telephony.SmsMessage
import android.util.Log

class SmsTrackReceiver : BroadcastReceiver() {

    private val assembler = RxTrackAssembler()

    override fun onReceive(context: Context, intent: Intent) {

        if (intent.action != "android.provider.Telephony.SMS_RECEIVED") return

        val bundle: Bundle? = intent.extras
        if (bundle == null) return

        val pdus = bundle.get("pdus") as Array<*>

        for (pdu in pdus) {

            val sms = SmsMessage.createFromPdu(pdu as ByteArray)

            val messageBody = sms.messageBody

            Log.d("SMS_RX", "received=$messageBody")

            if (!messageBody.startsWith("T#")) continue

            val points = assembler.processSms(messageBody)
            points?.let {

                TrackRepository.addPoints(it)

            }

            if (points != null) {

                Log.d("SMS_RX", "track points=${points.size}")

                for (p in points) {
                    Log.d("SMS_RX_POINT", "${p.first},${p.second}")
                }
            }
        }
    }
}