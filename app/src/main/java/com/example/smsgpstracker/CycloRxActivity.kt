package com.example.smsgpstracker.cyclosm

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity
import com.example.smsgpstracker.R

class CycloRxActivity : AppCompatActivity() {

    private lateinit var switchCyclo: Switch
    private lateinit var btnStartExport: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cyclo_rx)

        switchCyclo = findViewById(R.id.switchCyclo)
        btnStartExport = findViewById(R.id.btnStartExport)

        btnStartExport.setOnClickListener {
            if (switchCyclo.isChecked) {
                startCycloService()
            }
        }
    }

    private fun startCycloService() {
        val serviceIntent = Intent(this, CycloRxForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
}
