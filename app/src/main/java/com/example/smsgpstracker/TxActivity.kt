package com.example.smsgpstracker

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class TxActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    companion object {
        private const val PREFS_NAME = "SmsGpsTrackerPrefs"
        private const val KEY_PHONE = "phone_rx"
    }

    private lateinit var edtMaxSms: EditText
    private lateinit var edtInterval: EditText
    private lateinit var txtTimer: TextView
    private lateinit var txtSmsCounter: TextView
    private lateinit var btnStartTx: Button
    private lateinit var btnStopTx: Button
    private lateinit var btnSettings: Button
    private lateinit var imgLedTx: ImageView
    private lateinit var imgLedRx: ImageView
    private lateinit var txtGpsInfo: TextView
    private lateinit var txtSignalInfo: TextView
    private lateinit var signalBars: List<View>
    private lateinit var gpsBars: List<View>
    private lateinit var btnForcePosition: Button
    private lateinit var switchContinuousMode: Switch
    private lateinit var switchFastMonitor: Switch
    private var monitorIntervalMs = 5000L
    private val normalInterval = 5000L
    private val fastInterval = 500L
    private lateinit var switchMultiGpsSms: Switch
    private var multiGpsMode = false
    private lateinit var txtDebugConsole: TextView
    private var currentStatus = TxStatus.IDLE
    private var rxStatus = RxRemoteStatus.UNKNOWN
    private lateinit var switchNoSignalAlert: Switch
    enum class TxStatus { IDLE, WAITING, TRACKING }
    enum class RxRemoteStatus { UNKNOWN, ALIVE, LOST }

    private val debugReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context?, intent: Intent?) {

            val acc = intent?.getFloatExtra("accuracy", 0f) ?: 0f
            val buffer = intent?.getIntExtra("buffer", 0) ?: 0
            val seq = intent?.getIntExtra("seq", 0) ?: 0

            updateDebugConsole(
                acc,
                buffer,
                seq,
                "--"
            )
        }
    }

    private val updateReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context?, intent: Intent?) {

            intent ?: return

            val statusString = intent.getStringExtra("status")
            if (statusString != null) {

                val timer = intent.getIntExtra("timer", 0)
                val smsCount = intent.getIntExtra("smsCount", 0)

                val status = try {
                    TxStatus.valueOf(statusString)
                } catch (e: Exception) {
                    TxStatus.IDLE
                }

                updateUI(status, timer, smsCount)
                btnForcePosition.isEnabled = (status != TxStatus.IDLE)
                btnForcePosition.alpha = if (status != TxStatus.IDLE) 1f else 0.5f
            }

            if (intent.hasExtra("rxAlive")) {
                val rxAlive = intent.getBooleanExtra("rxAlive", false)
                updateRxLed(
                    if (rxAlive) RxRemoteStatus.ALIVE
                    else RxRemoteStatus.LOST
                )
            }

            if (intent.hasExtra("lat") && currentStatus != TxStatus.IDLE) {
                val lat = intent.getDoubleExtra("lat", 0.0)
                val lon = intent.getDoubleExtra("lon", 0.0)
                val acc = intent.getFloatExtra("accuracy", 999f)
                updateGpsUi(lat, lon, acc)
            }

            if (intent.hasExtra("signalDbm") && currentStatus != TxStatus.IDLE) {
                val dbm = intent.getIntExtra("signalDbm", -150)
                updateSignalUi(dbm)
            }
        }
    }

    fun updateDebugConsole(
        gpsAccuracy: Float,
        bufferSize: Int,
        seq: Int,
        lastSmsTime: String
    ) {

        val text = """
        GPS accuracy: $gpsAccuracy m
        Buffer points: $bufferSize
        SMS sequence: $seq
        Last SMS: $lastSmsTime
    """.trimIndent()

        txtDebugConsole.text = text
    }

    private fun forceSendPosition() {

        val intent = Intent(this, TxForegroundService::class.java).apply {
            action = "FORCE_POSITION"
        }

        startService(intent)
    }

    private fun updateUI(status: TxStatus, timer: Int, smsCount: Int) {

        currentStatus = status

        val minutes = timer / 60
        val seconds = timer % 60

        txtTimer.text = String.format("-%02d:%02d", minutes, seconds)
        txtSmsCounter.text = "SMS: $smsCount"

        updateTxLed(status)

        btnStartTx.isEnabled = (status == TxStatus.IDLE)
        btnStopTx.isEnabled = (status != TxStatus.IDLE)

        val isRunning = (status != TxStatus.IDLE)
        btnSettings.isEnabled = !isRunning
        btnSettings.alpha = if (isRunning) 0.5f else 1f

        // 👇 AGGIUNGI QUESTO
        switchFastMonitor.isEnabled = !isRunning

        if (isRunning) {
            switchFastMonitor.isChecked = false

            if (status == TxStatus.IDLE) {
                txtTimer.text = "00:00"
            } else {
                val minutes = timer / 60
                val seconds = timer % 60
                txtTimer.text = String.format("-%02d:%02d", minutes, seconds)
            }
        }

        if (status == TxStatus.IDLE) {
            resetSystemUi()
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onResume() {
        super.onResume()

        val debugFilter = IntentFilter("TX_DEBUG_UPDATE")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(debugReceiver, debugFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(debugReceiver, debugFilter)
        }

        restoreServiceState()

        val filter = IntentFilter().apply {
            addAction("com.example.smsgpstracker.TX_GPS_UPDATE")
            addAction(TxForegroundService.ACTION_UPDATE)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(updateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(updateReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(updateReceiver)
        } catch (_: Exception) {}

        try {
            unregisterReceiver(debugReceiver)
        } catch (_: Exception) {}
    }

    // =====================================================
    // ON CREATE
    // =====================================================


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_tx)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        txtDebugConsole = findViewById(R.id.txtDebugConsole)
        btnStartTx = findViewById(R.id.btnStartTx)
        btnStopTx = findViewById(R.id.btnStopTx)
        btnSettings = findViewById(R.id.btnSettings)
        btnForcePosition = findViewById(R.id.btnForcePosition)

        switchMultiGpsSms = findViewById(R.id.switchMultiGpsSms)
        switchContinuousMode = findViewById(R.id.switchContinuousMode)

        edtMaxSms = findViewById(R.id.edtMaxSms)
        edtInterval = findViewById(R.id.edtInterval)

        txtTimer = findViewById(R.id.txtTimer)
        txtSmsCounter = findViewById(R.id.txtSmsCounter)

        imgLedTx = findViewById(R.id.imgLedTx)
        imgLedRx = findViewById(R.id.imgLedRx)

        txtGpsInfo = findViewById(R.id.txtGpsInfo)
        txtSignalInfo = findViewById(R.id.txtSignalInfo)
        switchMultiGpsSms.setOnCheckedChangeListener { _, isChecked ->

            multiGpsMode = isChecked

            if (isChecked) {

                Log.d("TX_MODE", "MULTI GPS SMS MODE")

                // disabilita modalità classiche
                switchContinuousMode.isEnabled = false
                edtMaxSms.isEnabled = false
                edtInterval.isEnabled = false

            } else {

                Log.d("TX_MODE", "STANDARD SMS MODE")

                // riabilita modalità classiche
                switchContinuousMode.isEnabled = true
                edtInterval.isEnabled = true

                if (!switchContinuousMode.isChecked) {
                    edtMaxSms.isEnabled = true
                }
            }
        }

        signalBars = listOf(
            findViewById(R.id.s1),
            findViewById(R.id.s2),
            findViewById(R.id.s3),
            findViewById(R.id.s4),
            findViewById(R.id.s5)
        )

        gpsBars = listOf(
            findViewById(R.id.g1),
            findViewById(R.id.g2),
            findViewById(R.id.g3),
            findViewById(R.id.g4),
            findViewById(R.id.g5)
        )

        // Ripristino dati
        edtMaxSms.setText(prefs.getInt("maxSms", 5).toString())
        edtInterval.setText(prefs.getInt("interval", 10).toString())

        updateGpsUi(0.0, 0.0, 999f)
        updateRxLed(RxRemoteStatus.UNKNOWN)
        updateTxLed(TxStatus.IDLE)

        btnStartTx.setOnClickListener {

            Log.d("TX_UI", "START BUTTON CLICKED")

            // aggiorna subito la UI
            btnStartTx.isEnabled = false
            btnStartTx.alpha = 0.5f
            btnStopTx.isEnabled = true
            btnStopTx.alpha = 1f

            if (multiGpsMode) {

                Log.d("TX_MODE", "START MULTI GPS TRACKING")

                startMultiGpsTracking()

            } else {

                Log.d("TX_MODE", "START STANDARD TX")

                startTxService()
            }

        }
        btnStopTx.setOnClickListener {

            Log.d("TX_UI", "STOP BUTTON CLICKED")

            btnStartTx.isEnabled = true
            btnStartTx.alpha = 1f
            btnStopTx.isEnabled = false
            btnStopTx.alpha = 0.5f

            stopTxService() }
        btnForcePosition = findViewById(R.id.btnForcePosition)

        btnForcePosition.setOnClickListener {

            Log.d("TX_DEBUG", "PULSANTE PREMUTO")

            val intent = Intent(this, TxForegroundService::class.java).apply {
                action = TxForegroundService.ACTION_FORCE_POSITION
            }

            startService(intent)
        }

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        switchFastMonitor = findViewById(R.id.switchFastMonitor)

        switchFastMonitor.setOnCheckedChangeListener { _, isChecked ->

            if (currentStatus != TxStatus.IDLE) {
                switchFastMonitor.isChecked = false
                return@setOnCheckedChangeListener
            }

            val intent = Intent(this, TxForegroundService::class.java).apply {
                action = TxForegroundService.ACTION_SET_MONITOR_INTERVAL
                putExtra("intervalMs", if (isChecked) fastInterval else normalInterval)
            }

            startService(intent)
        }

        switchContinuousMode = findViewById(R.id.switchContinuousMode)

        switchContinuousMode.setOnCheckedChangeListener { _, isChecked ->
            if (multiGpsMode) return@setOnCheckedChangeListener

            if (isChecked) {
                edtMaxSms.isEnabled = false
            } else {
                edtMaxSms.isEnabled = true
            }

            edtMaxSms.isEnabled = !isChecked
            edtMaxSms.alpha = if (isChecked) 0.4f else 1f
        }



    }

    private fun startMultiGpsTracking() {

        val intent = Intent(this, TxForegroundService::class.java)

        intent.putExtra("MODE", "MULTI_GPS_SMS")

        ContextCompat.startForegroundService(this, intent)
    }

    // =====================================================
    // SERVICE CONTROL
    // =====================================================

    private fun startTxService() {



        val phone = prefs.getString(KEY_PHONE, null)

        if (phone.isNullOrBlank()) {
            Toast.makeText(this, "Numero RX non configurato in Settings", Toast.LENGTH_LONG).show()
            return
        }

        val continuousMode = switchContinuousMode.isChecked
        val maxSms = if (continuousMode) -1
        else edtMaxSms.text.toString().toIntOrNull() ?: 5
        val interval = edtInterval.text.toString().toIntOrNull() ?: 10
        val timeoutFactor = prefs.getFloat("timeout_factor", 2.0f)
        val rxTimeoutMs = (interval * timeoutFactor * 60 * 1000).toLong()


        saveLocalSettings(maxSms, interval)

        val intent = Intent(this, TxForegroundService::class.java).apply {
            action = TxForegroundService.ACTION_START
            putExtra("phone", phone)
            putExtra("maxSms", maxSms)
            putExtra("interval", interval)
            putExtra("rxTimeoutMs", rxTimeoutMs)
            putExtra("continuousMode", continuousMode)
            putExtra("noSignalAlert",
                switchNoSignalAlert.isChecked)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            startForegroundService(intent)
        else
            startService(intent)
    }

    private fun stopTxService() {
        val intent = Intent(this, TxForegroundService::class.java).apply {
            action = TxForegroundService.ACTION_STOP
        }
        startService(intent)
    }

    // =====================================================
    // PREFS
    // =====================================================

    private fun saveLocalSettings(maxSms: Int, interval: Int) {
        prefs.edit()
            .putInt("maxSms", maxSms)
            .putInt("interval", interval)
            .apply()
    }

    // =====================================================
    // UI HELPERS
    // =====================================================

    private fun updateTxLed(status: TxStatus) {
        val color = when (status) {
            TxStatus.IDLE -> Color.RED
            TxStatus.WAITING -> Color.YELLOW
            TxStatus.TRACKING -> Color.GREEN
        }
        imgLedTx.background.setTint(color)
    }

    private fun updateRxLed(status: RxRemoteStatus) {
        val color = when (status) {
            RxRemoteStatus.UNKNOWN -> Color.YELLOW
            RxRemoteStatus.ALIVE -> Color.GREEN
            RxRemoteStatus.LOST -> Color.RED
        }
        imgLedRx.background.setTint(color)
    }

    private fun updateGpsUi(lat: Double, lon: Double, accuracy: Float) {

        // 🔥 CASO: nessun fix ancora disponibile
        if (accuracy >= 900f) {

            txtGpsInfo.text = "--"

            // tutti LED bianchi (neutri)
            for (bar in gpsBars) {
                bar.background.setTint(Color.WHITE)
            }

            return
        }

        val gpsThreshold = prefs.getInt("gps_threshold", 20)

        txtGpsInfo.text = String.format(
            "%.5f, %.5f (±%.1fm)",
            lat, lon, accuracy
        )

        val level = when {
            accuracy <= 5 -> 5
            accuracy <= 10 -> 4
            accuracy <= 20 -> 3
            accuracy <= 40 -> 2
            else -> 1
        }

        updateBar(gpsBars, level)

        if (accuracy > gpsThreshold) {
            imgLedTx.background.setTint(Color.RED)
        }
    }

    private fun updateSignalUi(dbm: Int) {

        val signalThreshold = prefs.getInt("signal_threshold", -90)

        txtSignalInfo.text = "$dbm dBm"

        val level = when {
            dbm >= -75 -> 5
            dbm >= -85 -> 4
            dbm >= -95 -> 3
            dbm >= -105 -> 2
            else -> 1
        }

        updateBar(signalBars, level)

        if (dbm < signalThreshold) {
            imgLedTx.background.setTint(Color.RED)
        }
    }

    private fun updateBar(bars: List<View>, level: Int) {

        for (i in bars.indices) {

            val color = when {
                i < level && level >= 4 -> Color.GREEN
                i < level && level >= 3 -> Color.YELLOW
                i < level -> Color.RED
                else -> Color.LTGRAY
            }

            bars[i].background.setTint(color)
        }
    }

    private fun restoreServiceState() {

        val prefs = getSharedPreferences("tx_state_prefs", MODE_PRIVATE)

        val state = prefs.getString("tx_state", "IDLE")

        Log.d("TX_UI", "Restore state=$state")

        if (state == "TRACKING") {

            btnStartTx.isEnabled = false
            btnStartTx.alpha = 0.5f

            btnStopTx.isEnabled = true
            btnStopTx.alpha = 1f

        } else {

            btnStartTx.isEnabled = true
            btnStartTx.alpha = 1f

            btnStopTx.isEnabled = false
            btnStopTx.alpha = 0.5f
        }
    }

    private fun resetSystemUi() {

        // GPS
        txtGpsInfo.text = "--"
        for (bar in gpsBars) {
            bar.background.setTint(Color.WHITE)
        }

        // Rete
        txtSignalInfo.text = "-- dBm"
        for (bar in signalBars) {
            bar.background.setTint(Color.WHITE)
        }

        // LED RX torna UNKNOWN (giallo)
        updateRxLed(RxRemoteStatus.UNKNOWN)
    }

}