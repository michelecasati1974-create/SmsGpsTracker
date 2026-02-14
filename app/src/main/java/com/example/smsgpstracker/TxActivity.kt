package com.example.smsgpstracker

import android.content.*
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import android.annotation.SuppressLint

class TxActivity : AppCompatActivity() {

    private lateinit var edtPhoneRx: EditText
    private lateinit var edtMaxSms: EditText
    private lateinit var edtInterval: EditText
    private lateinit var imgLedGps: ImageView
    private lateinit var txtTimer: TextView
    private lateinit var txtSmsCounter: TextView
    private lateinit var btnStartTx: Button
    private lateinit var btnStopTx: Button

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {

            val isRunning =
                intent.getBooleanExtra("isRunning", false)

            val smsSent =
                intent.getIntExtra("smsSent", 0)

            val maxSms =
                intent.getIntExtra("maxSms", 0)

            val elapsed =
                intent.getLongExtra("elapsedSeconds", 0)

            val gpsFix =
                intent.getBooleanExtra("gpsFix", false)

            txtSmsCounter.text =
                "SMS: $smsSent/$maxSms"

            txtTimer.text =
                "Tempo: ${elapsed}s"

            imgLedGps.setImageResource(
                if (gpsFix)
                    R.drawable.led_green
                else
                    R.drawable.led_red
            )

            btnStartTx.isEnabled = !isRunning
            btnStopTx.isEnabled = isRunning
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
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

        btnStartTx.setOnClickListener { startTxService() }
        btnStopTx.setOnClickListener { stopTxService() }

        val filter =
            IntentFilter(TxForegroundService.ACTION_UPDATE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                updateReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            registerReceiver(updateReceiver, filter)
        }
    }

    override fun onResume() {
        super.onResume()

        val intent = Intent(
            this,
            TxForegroundService::class.java
        )

        intent.action =
            TxForegroundService.ACTION_REQUEST_STATE

        startService(intent)
    }

    private fun startTxService() {

        val intent =
            Intent(this, TxForegroundService::class.java)

        intent.action =
            TxForegroundService.ACTION_START

        intent.putExtra(
            "phone",
            edtPhoneRx.text.toString()
        )

        intent.putExtra(
            "maxSms",
            edtMaxSms.text.toString().toIntOrNull() ?: 0
        )

        intent.putExtra(
            "interval",
            edtInterval.text.toString().toIntOrNull() ?: 1
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopTxService() {

        val intent =
            Intent(this, TxForegroundService::class.java)

        intent.action =
            TxForegroundService.ACTION_STOP

        startService(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(updateReceiver)
    }
}
