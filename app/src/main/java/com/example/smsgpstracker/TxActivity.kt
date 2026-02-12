package com.example.smsgpstracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class TxActivity : AppCompatActivity() {

    private var edtPhoneRx: EditText? = null
    private var edtMaxSms: EditText? = null
    private var edtInterval: EditText? = null
    private var imgLedGps: ImageView? = null
    private var txtTimer: TextView? = null
    private var txtSmsCounter: TextView? = null
    private var btnStartTx: Button? = null
    private var btnStopTx: Button? = null

    private val updateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {

            val isRunning = intent.getBooleanExtra("isRunning", false)
            val smsSent = intent.getIntExtra("smsSent", 0)
            val maxSms = intent.getIntExtra("maxSms", 0)
            val secondsRemaining = intent.getIntExtra("secondsRemaining", 0)
            val gpsFix = intent.getBooleanExtra("gpsFix", false)

            txtSmsCounter?.text = "SMS: $smsSent/$maxSms"

            val min = secondsRemaining / 60
            val sec = secondsRemaining % 60
            txtTimer?.text = String.format("%02d:%02d", min, sec)

            imgLedGps?.setImageResource(
                if (gpsFix) R.drawable.led_green else R.drawable.led_red
            )

            btnStartTx?.isEnabled = !isRunning
            btnStopTx?.isEnabled = isRunning
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tx)

        edtPhoneRx = findViewById(R.id.edtPhoneRx)
        edtMaxSms = findViewById(R.id.edtMaxSms)
        edtInterval = findViewById(R.id.edtInterval)
        imgLedGps = findViewById(R.id.imgLedGps)
        txtTimer = findViewById(R.id.txtTimer)
        txtSmsCounter = findViewById(R.id.txtSmsCounter)
        btnStartTx = findViewById(R.id.btnStartTx)
        btnStopTx = findViewById(R.id.btnStopTx)

        btnStartTx?.setOnClickListener { startTxService() }
        btnStopTx?.setOnClickListener { stopTxService() }

        val filter = IntentFilter(TxForegroundService.ACTION_UPDATE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                updateReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(updateReceiver, filter)
        }
    }

    private fun startTxService() {
        val intent = Intent(this, TxForegroundService::class.java)
        intent.action = TxForegroundService.ACTION_START
        intent.putExtra("phone", edtPhoneRx?.text.toString())
        intent.putExtra("maxSms", edtMaxSms?.text.toString().toIntOrNull() ?: 0)
        intent.putExtra("interval", edtInterval?.text.toString().toIntOrNull() ?: 0)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopTxService() {
        val intent = Intent(this, TxForegroundService::class.java)
        intent.action = TxForegroundService.ACTION_STOP
        startService(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(updateReceiver)
    }
}