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
    private lateinit var txtCounter: TextView
    private lateinit var inputMaxSms: EditText
    private lateinit var inputInterval: EditText
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button

    private val handler = Handler(Looper.getMainLooper())
    private var smsSent = 0
    private var maxSms = 0
    private var intervalMs = 0L
    private var isRunning = false

    private val txRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return

            smsSent++
            txtCounter.text = "SMS inviati: $smsSent / $maxSms"

            if (smsSent >= maxSms) {
                stopTx()
            } else {
                handler.postDelayed(this, intervalMs)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tx)

        txtStatus = findViewById(R.id.txtStatus)
        txtCounter = findViewById(R.id.txtCounter)
        inputMaxSms = findViewById(R.id.inputMaxSms)
        inputInterval = findViewById(R.id.inputInterval)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)

        btnStart.setOnClickListener { startTx() }
        btnStop.setOnClickListener { stopTx() }
    }

    private fun startTx() {
        val maxSmsText = inputMaxSms.text.toString()
        val intervalText = inputInterval.text.toString()

        if (maxSmsText.isEmpty() || intervalText.isEmpty()) {
            txtStatus.text = "Inserisci tutti i valori"
            return
        }

        maxSms = maxSmsText.toInt()
        intervalMs = intervalText.toLong() * 60_000 // minuti → ms

        smsSent = 0
        isRunning = true

        txtStatus.text = "TX ATTIVO"
        txtCounter.text = "SMS inviati: 0 / $maxSms"

        handler.postDelayed(txRunnable, intervalMs)
    }

    private fun stopTx() {
        isRunning = false
        handler.removeCallbacks(txRunnable)
        txtStatus.text = "TX FERMO"
    }
}


