package com.example.smsgpstracker

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class TxActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tx)

        val txtStatus = findViewById<TextView>(R.id.txtStatus)
        val inputMaxSms = findViewById<EditText>(R.id.inputMaxSms)
        val inputInterval = findViewById<EditText>(R.id.inputInterval)
        val btnStart = findViewById<Button>(R.id.btnStart)
        val btnStop = findViewById<Button>(R.id.btnStop)

        btnStart.setOnClickListener {
            txtStatus.text = "TX ATTIVO"
        }

        btnStop.setOnClickListener {
            txtStatus.text = "TX FERMO"
        }
    }
}

