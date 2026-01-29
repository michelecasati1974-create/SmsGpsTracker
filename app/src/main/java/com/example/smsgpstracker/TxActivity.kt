package com.example.smsgpstracker

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class TxActivity : AppCompatActivity() {

    private lateinit var txtStatus: TextView
    private lateinit var inputMaxSms: EditText
    private lateinit var inputInterval: EditText
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button

    private val handler = Handler(Looper.getMainLooper())
    private var smsRemaining = 0
    private var intervalMs = 0L
    private var isRunning = false

    private val txRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return

            if (smsRemaining > 0) {
                smsRemaining--
                txtStatus.text = "TX ATTIVO – SMS rimasti: $smsRemaining"

                // Simulazione invio SMS (LOG)
                println("TX → SMS simulato inviato")

                handler.postDelayed(this, intervalMs)
            } else {
                stopTx()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tx)

        txtStatus = findViewById(R.id.txtStatus)
        inputMaxSms = findViewById(R.id.inputMaxSms)
        inputInterval = findViewById(R.id.inputInterval)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)

        btnStart.setOnClickListener { startTx() }
        btnStop.setOnClickListener { stopTx() }
    }

    private fun startTx() {
        if (isRunning) return

        val maxSms = inputMaxSms.text.toString().toIntOrNull()
        val intervalSec = inputInterval.text.toString().toLongOrNull()

        if (maxSms == null || intervalSec == null || maxSms <= 0 || intervalSec <= 0) {
            txtStatus.text = "Parametri non validi"
            return
        }

        smsRemaining = maxSms
        intervalMs = intervalSec * 1000 // per test usiamo secondi
        isRunning = true

        txtStatus.text = "TX ATTIVO – SMS rimasti: $smsRemaining"
        handler.post(txRunnable)
    }

    private fun stopTx() {
        isRunning = false
        handler.removeCallbacks(txRunnable)
        txtStatus.text = "TX FERMO"
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(txRunnable)
    }
}



