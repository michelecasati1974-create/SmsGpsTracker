package com.example.smsgpstracker

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnTx = findViewById<Button>(R.id.btnTx)
        val btnRx = findViewById<Button>(R.id.btnRx)

        // MODALITÀ TRASMISSIONE
        btnTx.setOnClickListener {
            startActivity(Intent(this, TxActivity::class.java))
        }

        // MODALITÀ RICEZIONE
        btnRx.setOnClickListener {
            Toast.makeText(this, "Modalità RX attiva", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, RxActivity::class.java))
        }
    }
}

