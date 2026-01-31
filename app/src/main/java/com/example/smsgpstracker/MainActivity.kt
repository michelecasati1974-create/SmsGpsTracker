package com.example.smsgpstracker

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.util.Log
import com.example.smsgpstracker.tx.GpsHelper

class MainActivity : AppCompatActivity() {

    private val SMS_PERMISSION_CODE = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Controllo e richiesta permessi SMS + GPS
        checkAndRequestSmsPermissions()

        // Inizializza helper GPS
        GpsHelper.init(this)

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

    private fun checkAndRequestSmsPermissions() {
        val permissions = arrayOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                missingPermissions.toTypedArray(),
                SMS_PERMISSION_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == SMS_PERMISSION_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Log.d("PERMISSION", "Permessi SMS e GPS concessi")
            } else {
                Log.e("PERMISSION", "Permessi negati")
            }
        }
    }
}


