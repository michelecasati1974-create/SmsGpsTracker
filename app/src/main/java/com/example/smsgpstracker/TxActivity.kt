package com.example.smsgpstracker



import android.content.*
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import android.annotation.SuppressLint
import android.graphics.Color
import android.util.Log


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


    private val updateReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context?, intent: Intent?) {

            val statusString = intent?.getStringExtra("status") ?: return
            val timer = intent.getIntExtra("timer", 0)
            val smsCount = intent.getIntExtra("smsCount", 0)

            txtTimer.text = "Tempo: $timer s"
            txtSmsCounter.text = "SMS: $smsCount"

            val status = TxStatus.valueOf(statusString)
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


    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tx)

        // =====================================================
        // UI BINDING
        // =====================================================

        edtPhoneRx = findViewById(R.id.edtPhoneRx)
        edtMaxSms = findViewById(R.id.edtMaxSms)
        edtInterval = findViewById(R.id.edtInterval)
        imgLedGps = findViewById(R.id.imgLedGps)
        txtTimer = findViewById(R.id.txtTimer)
        txtSmsCounter = findViewById(R.id.txtSmsCounter)
        btnStartTx = findViewById(R.id.btnStartTx)
        btnStopTx = findViewById(R.id.btnStopTx)

        // =====================================================
        // SHARED PREFERENCES
        // =====================================================

        prefs = getSharedPreferences("TX_PREFS", MODE_PRIVATE)
        val savedStatus =
            prefs.getString("ledStatus", TxStatus.IDLE.name)

        updateLed(TxStatus.valueOf(savedStatus!!))

        // Carica valori salvati
        edtPhoneRx.setText(prefs.getString("phone", ""))
        edtMaxSms.setText(prefs.getInt("maxSms", 5).toString())
        edtInterval.setText(prefs.getInt("interval", 10).toString())

        // =====================================================
        // BUTTON LISTENERS
        // =====================================================

        btnStartTx.setOnClickListener {
            saveSettings()      // 🔥 Salva prima di partire
            startTxService()

        }

        btnStopTx.setOnClickListener {
            stopTxService()
        }
        when (TxStatus.valueOf(savedStatus!!)) {
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

        // =====================================================
        // RECEIVER UPDATE DA SERVICE
        // =====================================================

        val filter = IntentFilter(TxForegroundService.ACTION_UPDATE)


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                updateReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            registerReceiver(updateReceiver, filter)
        }
    }




    private fun startTxService() {

        val intent =
            Intent(this, TxForegroundService::class.java)

        intent.action =
            TxForegroundService.ACTION_START

        intent.putExtra(
            "phone",
            edtPhoneRx.text.toString()
        )

        intent.putExtra(
            "maxSms",
            edtMaxSms.text.toString().toIntOrNull() ?: 0
        )

        intent.putExtra(
            "interval",
            edtInterval.text.toString().toIntOrNull() ?: 1
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        if (edtPhoneRx.text.toString().isBlank()) {
            Toast.makeText(this, "Inserisci numero telefono", Toast.LENGTH_SHORT).show()
            return
        }
    }

    private fun stopTxService() {

        val intent =
            Intent(this, TxForegroundService::class.java)

        intent.action =
            TxForegroundService.ACTION_STOP

        startService(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(updateReceiver)
        } catch (e: Exception) {}
    }

    private fun saveSettings() {

        val phone = edtPhoneRx.text.toString()
        val maxSms = edtMaxSms.text.toString().toIntOrNull() ?: 5
        val interval = edtInterval.text.toString().toIntOrNull() ?: 10

        prefs.edit()
            .putString("phone", phone)
            .putInt("maxSms", maxSms)
            .putInt("interval", interval)
            .apply()
    }
    enum class TxStatus {
        IDLE,
        WAITING,
        TRACKING
    }
    private var currentStatus = TxStatus.IDLE

    private fun updateLed(status: TxStatus) {

        currentStatus = status

        val colorInt = when (status) {
            TxStatus.IDLE -> Color.RED
            TxStatus.WAITING -> Color.YELLOW
            TxStatus.TRACKING -> Color.GREEN
        }

        imgLedGps.setColorFilter(colorInt, android.graphics.PorterDuff.Mode.SRC_IN)

        prefs.edit()
            .putString("ledStatus", status.name)
            .apply()
    }



}
