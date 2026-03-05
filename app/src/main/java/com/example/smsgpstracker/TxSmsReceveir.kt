package com.example.smsgpstracker

import android.content.*
import android.provider.Telephony

class TxSmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION != intent.action) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

        for (sms in messages) {

            val body = sms.messageBody ?: continue

            when {

                body.startsWith("GPS:") -> {

                    val uiIntent =
                        Intent(TxForegroundService.ACTION_UPDATE)

                    uiIntent.putExtra("rxAlive", true)
                    uiIntent.setPackage(context.packageName)

                    context.sendBroadcast(uiIntent)
                }

                body == "RX_ABORT" -> {

                    val abortIntent =
                        Intent(context, TxForegroundService::class.java)

                    abortIntent.action = TxForegroundService.ACTION_ABORT
                    context.startService(abortIntent)

                    // aggiorna LED RX
                    val uiIntent =
                        Intent(TxForegroundService.ACTION_UPDATE)

                    uiIntent.putExtra("rxLost", true)
                    uiIntent.setPackage(context.packageName)

                    context.sendBroadcast(uiIntent)
                }

                body == "CTRL:END_OK" -> {

                    val uiIntent =
                        Intent(TxForegroundService.ACTION_UPDATE)

                    uiIntent.putExtra("rxAlive", true)
                    uiIntent.setPackage(context.packageName)

                    context.sendBroadcast(uiIntent)
                }
            }
        }
    }
}