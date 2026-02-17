package com.example.smsgpstracker

import android.annotation.SuppressLint
import android.content.*
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class TxActivity : AppCompatActivity() {

    private lateinit var edtPhoneRx: EditText
    private lateinit var edtMaxSms: EditText
    private lateinit var edtInterval: EditText
    private lateinit var imgLedGps: ImageView
    private lateinit var txtTimer: TextView
    private lateinit var txtSmsCounter: TextView
    private lateinit var btnStartTx: Button
    private lateinit var btnStopTx: Button
    private lateinit var prefs: SharedPreferences

    private var currentStatus = TxStatus.IDLE

    enum class TxStatus {
        IDLE,
        WAITING,
        TRACKING
    }

    // =====================================================
    // RECEIVER DAL SERVICE
    // =====================================================

    private val updateReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context?, intent: Intent?) {

            val statusString = intent?.getStringExtra("status") ?: return
            val timer = intent.getIntExtra("timer", 0)
            val smsCount = intent.getIntExtra("smsCount", 0)

            val status = try {
                TxStatus.valueOf(statusString)
            } catch (e: Exception) {
                TxStatus.IDLE
            }

            updateUI(status, timer, smsCount)
        }
    }

    // =====================================================
    // ON CREATE
    // =====================================================

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tx)

        // UI
        edtPhoneRx = findViewById(R.id.edtPhoneRx)
        edtMaxSms = findViewById(R.id.edtMaxSms)
        edtInterval = findViewById(R.id.edtInterval)
        imgLedGps = findViewById(R.id.imgLedGps)
        txtTimer = findViewById(R.id.txtTimer)
        txtSmsCounter = findViewById(R.id.txtSmsCounter)
        btnStartTx = findViewById(R.id.btnStartTx)
        btnStopTx = findViewById(R.id.btnStopTx)

        prefs = getSharedPreferences("TX_PREFS", MODE_PRIVATE)

        // Ripristino dati salvati
        edtPhoneRx.setText(prefs.getString("phone", ""))
        edtMaxSms.setText(prefs.getInt("maxSms", 5).toString())
        edtInterval.setText(prefs.getInt("interval", 10).toString())

        val savedStatus =
            prefs.getString("ledStatus", TxStatus.IDLE.name)

        val restoredStatus = try {
            TxStatus.valueOf(savedStatus!!)
        } catch (e: Exception) {
            TxStatus.IDLE
        }

        updateUI(restoredStatus, 0, 0)

        // LISTENER
        btnStartTx.setOnClickListener { startTxService() }
        btnStopTx.setOnClickListener { stopTxService() }

        // Receiver
        val filter = IntentFilter(TxForegroundService.ACTION_UPDATE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(updateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(updateReceiver, filter)
        }
    }

    // =====================================================
    // AVVIO SERVICE
    // =====================================================

    private fun startTxService() {

        val phone = edtPhoneRx.text.toString()

        if (phone.isBlank()) {
            Toast.makeText(this, "Inserisci numero telefono", Toast.LENGTH_SHORT).show()
            return
        }

        val maxSms = edtMaxSms.text.toString().toIntOrNull() ?: 5
        val interval = edtInterval.text.toString().toIntOrNull() ?: 10

        saveSettings(phone, maxSms, interval)

        val intent = Intent(this, TxForegroundService::class.java).apply {
            action = TxForegroundService.ACTION_START
            putExtra("phone", phone)
            putExtra("maxSms", maxSms)
            putExtra("interval", interval)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopTxService() {

        val intent = Intent(this, TxForegroundService::class.java).apply {
            action = TxForegroundService.ACTION_STOP
        }

        startService(intent)
    }

    // =====================================================
    // UI UPDATE CENTRALIZZATA
    // =====================================================

    private fun updateUI(status: TxStatus, timer: Int, smsCount: Int) {

        currentStatus = status

        txtTimer.text = "Tempo: $timer s"
        txtSmsCounter.text = "SMS: $smsCount"

        updateLed(status)

        when (status) {
            TxStatus.IDLE -> {
                btnStartTx.isEnabled = true
                btnStopTx.isEnabled = false
            }
            TxStatus.WAITING,
            TxStatus.TRACKING -> {
                btnStartTx.isEnabled = false
                btnStopTx.isEnabled = true
            }
        }
    }

    // =====================================================
    // LED GPS
    // =====================================================

    private fun updateLed(status: TxStatus) {

        val colorInt = when (status) {
            TxStatus.IDLE -> Color.RED
            TxStatus.WAITING -> Color.YELLOW
            TxStatus.TRACKING -> Color.GREEN
        }

        imgLedGps.setColorFilter(
            colorInt,
            android.graphics.PorterDuff.Mode.SRC_IN
        )

        prefs.edit()
            .putString("ledStatus", status.name)
            .apply()
    }

    // =====================================================
    // SALVATAGGIO IMPOSTAZIONI
    // =====================================================

    private fun saveSettings(phone: String, maxSms: Int, interval: Int) {

        prefs.edit()
            .putString("phone", phone)
            .putInt("maxSms", maxSms)
            .putInt("interval", interval)
            .apply()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(updateReceiver)
        } catch (_: Exception) {}
    }
}

