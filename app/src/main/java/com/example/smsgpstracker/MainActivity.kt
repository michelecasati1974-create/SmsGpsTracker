package com.example.smsgpstracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.smsgpstracker.tx.GpsHelper
import com.example.smsgpstracker.tx.NotificationHelper

class MainActivity : AppCompatActivity() {

    private val SMS_PERMISSION_CODE = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 🔐 Permessi runtime
        checkAndRequestPermissions()

        // 📡 GPS helper
        GpsHelper.init(this)

        // 🔔 Canale notifiche
        NotificationHelper.createChannel(this)

        val btnTx = findViewById<Button>(R.id.btnTx)
        val btnRx = findViewById<Button>(R.id.btnRx)

        btnTx.setOnClickListener {
            startActivity(Intent(this, TxActivity::class.java))
        }

        btnRx.setOnClickListener {


            // ✅ OK → RX
            Toast.makeText(this, "Modalità RX attiva", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, RxActivity::class.java))
        }
    }

    // ============================
    // 🔍 VERIFICA APP SMS DEFAULT
    // ============================
    private fun isDefaultSmsApp(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            Telephony.Sms.getDefaultSmsPackage(this) == packageName
        } else {
            true
        }
    }

    // ============================
    // 📲 RICHIESTA UFFICIALE ANDROID
    // ============================
    private fun requestDefaultSmsApp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
            intent.putExtra(
                Telephony.Sms.Intents.EXTRA_PACKAGE_NAME,
                packageName
            )
            startActivity(intent)
        }
    }

    // ============================
    // 🔐 PERMESSI
    // ============================
    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

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
                Log.d("PERMISSION", "Permessi concessi")
            } else {
                Log.e("PERMISSION", "Alcuni permessi negati")
                Toast.makeText(
                    this,
                    "Permessi necessari per il corretto funzionamento",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}


