package com.example.smsgpstracker

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class RxActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rx)

        val sender = intent.getStringExtra("sender") ?: "Sconosciuto"
        val message = intent.getStringExtra("message") ?: ""
        val time = intent.getLongExtra("time", 0L)

        val txtInfo = findViewById<TextView>(R.id.txtInfo)

        txtInfo.text = """
            SMS ricevuto
            Da: $sender
            Testo: $message
            Timestamp: $time
        """.trimIndent()
    }
}
