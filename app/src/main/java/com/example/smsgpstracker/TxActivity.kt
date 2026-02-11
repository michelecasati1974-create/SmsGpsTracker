package com.example.smsgpstracker

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.SmsManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*

class TxActivity : AppCompatActivity() {

    // UI
    private lateinit var edtPhoneRx: EditText
    private lateinit var edtMaxSms: EditText
    private lateinit var edtInterval: EditText
    private lateinit var imgLedGps: ImageView
    private lateinit var txtTimer: TextView
    private lateinit var txtSmsCounter: TextView
    private lateinit var btnStartTx: Button
    private lateinit var btnStopTx: Button

    // Preferences
    private lateinit var prefs: SharedPreferences

    // GPS
    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false

    private var maxSms = 0
    private var smsSent = 0
    private var intervalMs = 0L
    private var windowStart = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tx)

        edtPhoneRx = findViewById(R.id.edtPhoneRx)
        edtMaxSms = findViewById(R.id.edtMaxSms)
        edtInterval = findViewById(R.id.edtInterval)
        imgLedGps = findViewById(R.id.imgLedGps)
        txtTimer = findViewById(R.id.txtTimer)
        txtSmsCounter = findViewById(R.id.txtSmsCounter)
        btnStartTx = findViewById(R.id.btnStartTx)
        btnStopTx = findViewById(R.id.btnStopTx)

        prefs = getSharedPreferences("TX_PREFS", Context.MODE_PRIVATE)

        loadSavedParameters()

        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        setupLocationCallback()

        btnStartTx.setOnClickListener { startTx() }
        btnStopTx.setOnClickListener { stopTx() }

        resetUi()
    }

    // =========================
    // SALVATAGGIO PARAMETRI
    // =========================

    private fun loadSavedParameters() {
        edtPhoneRx.setText(prefs.getString("phone", ""))
        edtMaxSms.setText(prefs.getInt("maxSms", 0).takeIf { it > 0 }?.toString() ?: "")
        edtInterval.setText(prefs.getLong("interval", 0L).takeIf { it > 0 }?.toString() ?: "")
    }

    private fun saveParameters(phone: String, maxSms: Int, intervalMin: Long) {
        prefs.edit()
            .putString("phone", phone)
            .putInt("maxSms", maxSms)
            .putLong("interval", intervalMin)
            .apply()
    }

    // =========================
    // START / STOP
    // =========================

    private fun startTx() {

        if (isRunning) return

        val phone = edtPhoneRx.text.toString().trim()
        val nSms = edtMaxSms.text.toString().toIntOrNull()
        val intervalMin = edtInterval.text.toString().toLongOrNull()

        if (phone.isEmpty() || nSms == null || intervalMin == null ||
            nSms <= 0 || intervalMin <= 0) {
            Toast.makeText(this, "Parametri non validi", Toast.LENGTH_SHORT).show()
            return
        }

        saveParameters(phone, nSms, intervalMin)

        maxSms = nSms
        intervalMs = intervalMin * 60_000L
        smsSent = 0
        windowStart = System.currentTimeMillis()
        isRunning = true

        btnStartTx.isEnabled = false
        btnStopTx.isEnabled = true

        txtSmsCounter.text = "SMS: 0/$maxSms"

        sendControlSms("CTRL:START")
        startGps()
        startTimer()
    }

    private fun stopTx() {
        if (!isRunning) return

        sendControlSms("CTRL:STOP")

        isRunning = false
        handler.removeCallbacksAndMessages(null)
        fusedClient.removeLocationUpdates(locationCallback)

        resetUi()
    }

    private fun resetUi() {
        btnStartTx.isEnabled = true
        btnStopTx.isEnabled = false
        txtSmsCounter.text = "SMS: 0/0"
        txtTimer.text = "00:00"
    }

    // =========================
    // GPS
    // =========================

    private fun setupLocationCallback() {

        locationCallback = object : LocationCallback() {

            override fun onLocationResult(result: LocationResult) {

                if (!isRunning) return

                val location = result.lastLocation

                if (location != null) {

                    // GPS FIX OK → LED VERDE
                    runOnUiThread {
                        imgLedGps.setImageResource(R.drawable.led_green)
                    }

                    handleLocation(location)

                } else {

                    // GPS in ricerca → LED GIALLO
                    runOnUiThread {
                        imgLedGps.setImageResource(R.drawable.led_yellow)
                    }
                    imgLedGps.setImageResource(R.drawable.led_red)
                }
            }
        }
    }


    private fun startGps() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                100
            )
            return
        }

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            2000L
        ).build()

        fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    private fun handleLocation(loc: Location) {

        if (!isRunning) return

        val now = System.currentTimeMillis()

        if (now - windowStart >= intervalMs) {

            if (smsSent >= maxSms) {
                sendControlSms("CTRL:END")
                stopTx()
                return
            }

            val msg = "GPS:${loc.latitude},${loc.longitude}"
            SmsManager.getDefault().sendTextMessage(
                edtPhoneRx.text.toString(),
                null,
                msg,
                null,
                null
            )

            smsSent++
            txtSmsCounter.text = "SMS: $smsSent/$maxSms"
            windowStart = now
        }
    }

    private fun sendControlSms(msg: String) {
        SmsManager.getDefault().sendTextMessage(
            edtPhoneRx.text.toString(),
            null,
            msg,
            null,
            null
        )
    }

    // =========================
    // TIMER
    // =========================

    private fun startTimer() {
        handler.post(object : Runnable {
            override fun run() {
                if (!isRunning) return
                val elapsed = (System.currentTimeMillis() - windowStart) / 1000
                txtTimer.text = String.format("%02d:%02d", elapsed / 60, elapsed % 60)
                handler.postDelayed(this, 1000)
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTx()
    }
}

