package com.example.smsgpstracker.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.smsgpstracker.R

class MainActivity : AppCompatActivity() {

    private val PERMISSION_REQUEST_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val tvStatus = findViewById<TextView>(R.id.tvStatus)
        val tvPermissions = findViewById<TextView>(R.id.tvPermissions)
        val btnToggle = findViewById<Button>(R.id.btnToggle)

        updatePermissionStatus(tvPermissions)
        tvStatus.text = "Servizio: NON ATTIVO"

        btnToggle.setOnClickListener {
            if (!hasAllPermissions()) {
                requestPermissions()
                return@setOnClickListener
            }

            tvStatus.text = "Servizio: PRONTO"
        }
    }

    private fun hasAllPermissions(): Boolean {
        val permissions = arrayOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.SEND_SMS,
                Manifest.permission.ACCESS_FINE_LOCATION
            ),
            PERMISSION_REQUEST_CODE
        )
    }

    private fun updatePermissionStatus(tv: TextView) {
        tv.text = if (hasAllPermissions()) {
            "Permessi: CONCESSI"
        } else {
            "Permessi: MANCANTI"
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            updatePermissionStatus(findViewById(R.id.tvPermissions))
        }
    }
}

