package com.example.smsgpstracker

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class TxActivity : AppCompatActivity() {

    private var isRunning = false
    private var sentCount = 0
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tx)

        val maxSmsInput = findViewById<EditText>(R.id.inputMaxSms)
        val intervalInput = findViewById<EditText>(R.id.inputInterval)
        val startButton = findViewById<Button>(R.id.btnStart)
        statusText = findViewById(R.id.txtStatus)

        startButton.setOnClickListener {
            if (isRunning) return@setOnClickListener

            val maxSms = maxSmsInput.text.toString().toIntOrNull()
            val intervalMin = intervalInput.text.toString().toIntOrNull()

            if (maxSms == null || intervalMin == null || maxSms <= 0 || intervalMin <= 0) {
                Toast.makeText(this, "Valori non validi", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            startSending(maxSms, intervalMin)
        }
    }

    private fun startSending(maxSms: Int, intervalMin: Int) {
        isRunning = true
        sentCount = 0
        statusText.text = "STATO: RUNNING"

        val handler = Handler(Looper.getMainLooper())
        val intervalMs = intervalMin * 60 * 1000L

        val task = object : Runnable {
            override fun run() {
                if (sentCount >= maxSms) {
                    isRunning = false
                    statusText.text = "STATO: STOP"
                    Toast.makeText(this@TxActivity, "Invio completato", Toast.LENGTH_LONG).show()
                    return
                }

                sentCount++
                Toast.makeText(
                    this@TxActivity,
                    "Simulato invio SMS #$sentCount",
                    Toast.LENGTH_SHORT
                ).show()

                handler.postDelayed(this, intervalMs)
            }
        }

        handler.post(task)
    }
}
