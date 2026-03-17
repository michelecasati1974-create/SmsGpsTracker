package com.example.smsgpstracker

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SmsDebugActivity : AppCompatActivity() {

    private lateinit var txtLog: TextView

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.smsdebugactivity)

        txtLog = findViewById(R.id.txtSmsLog)

        refreshLog()
    }

    private fun refreshLog() {

        val logs = SmsDebugManager.getLogs()

        val builder = StringBuilder()

        for (i in 0 until logs.size) {

            builder.append(logs[i].toString())
            builder.append("\n")

        }

        txtLog.text = builder.toString()
    }
}