package com.example.smsgpstracker

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.text.method.PasswordTransformationMethod
import android.text.method.HideReturnsTransformationMethod
import android.widget.Switch
import android.content.Intent

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    private lateinit var edtPhoneRx: EditText
    private lateinit var edtSignalThreshold: EditText
    private lateinit var edtGpsThreshold: EditText
    private lateinit var edtTimeoutFactor: EditText
    private lateinit var btnSaveSettings: Button
    private lateinit var btnCancelSettings: Button

    private lateinit var switchDebugTrack: Switch

    private lateinit var edtNoSignalTc: EditText
    private lateinit var edtVibrationTs: EditText
    private lateinit var switchAutoMode: Switch

    private lateinit var btnTrainGpx: Button




    companion object {
        private const val KEY_DEBUG_TRACK = "debug_track"
        private const val PREFS_NAME = "SmsGpsTrackerPrefs"
        private const val KEY_PHONE = "phone_rx"
        private const val KEY_SIGNAL = "signal_threshold"
        private const val KEY_GPS = "gps_threshold"
        private const val KEY_TIMEOUT = "timeout_factor"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)

        val btnMultiGps = findViewById<Button>(R.id.btnMultiGpsSettings)

        btnMultiGps.setOnClickListener {
            val intent = Intent(this, MultiGpsSettingsActivity::class.java)
            startActivity(intent)
        }

        // Bind UI
        switchAutoMode = findViewById(R.id.switchAutoMode)
        switchDebugTrack = findViewById<Switch>(R.id.switchDebugTrack)
        edtPhoneRx = findViewById(R.id.edtPhoneRx)
        edtSignalThreshold = findViewById(R.id.edtSignalThreshold)
        edtGpsThreshold = findViewById(R.id.edtGpsThreshold)
        edtTimeoutFactor = findViewById(R.id.edtTimeoutFactor)
        btnSaveSettings = findViewById(R.id.btnSaveSettings)
        btnCancelSettings = findViewById(R.id.btnCancelSettings)
        edtNoSignalTc = findViewById(R.id.edtNoSignalTc)
        edtVibrationTs = findViewById(R.id.edtVibrationTs)


        // UNA sola SharedPreferences
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        // Caricamento valori Tc e Ts
        val savedTc = prefs.getInt("noSignalTc", 10)
        val savedTs = prefs.getInt("vibrationTs", 3)

        edtNoSignalTc.setText(savedTc.toString())
        edtVibrationTs.setText(savedTs.toString())

        btnTrainGpx = findViewById(R.id.btnTrainGpx)

        btnTrainGpx.setOnClickListener {

            Thread {
                GpxTrainingManager.runTraining(this)

                runOnUiThread {
                    Toast.makeText(
                        this,
                        "Training completato",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }.start()
        }

        loadSettings()

        btnSaveSettings.setOnClickListener {

            val Tc = edtNoSignalTc.text.toString().toIntOrNull() ?: 10
            val Ts = edtVibrationTs.text.toString().toIntOrNull() ?: 3

            if (Tc !in 1..300) {
                Toast.makeText(this, "Tc deve essere tra 1 e 300 sec", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            if (Ts !in 1..60) {
                Toast.makeText(this, "Ts deve essere tra 1 e 60 sec", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            prefs.edit()
                .putInt("noSignalTc", Tc)
                .putInt("vibrationTs", Ts)
                .apply()

            saveSettings()
        }

        btnCancelSettings.setOnClickListener {
            finish()
        }
    }

    private fun loadSettings() {

        switchAutoMode.isChecked =
            prefs.getBoolean("auto_mode_enabled", true)

        switchDebugTrack.isChecked =
            prefs.getBoolean(KEY_DEBUG_TRACK, false)

        edtPhoneRx.transformationMethod = PasswordTransformationMethod.getInstance()

        edtPhoneRx.setText(prefs.getString(KEY_PHONE, ""))

        edtSignalThreshold.setText(
            prefs.getInt(KEY_SIGNAL, -90).toString()
        )

        edtGpsThreshold.setText(
            prefs.getInt(KEY_GPS, 20).toString()
        )

        edtTimeoutFactor.setText(
            prefs.getFloat(KEY_TIMEOUT, 2.0f).toString()
        )

        edtPhoneRx.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                edtPhoneRx.transformationMethod = HideReturnsTransformationMethod.getInstance()
            } else {
                edtPhoneRx.transformationMethod = PasswordTransformationMethod.getInstance()
            }
        }
    }

    private fun saveSettings() {

        val phone = edtPhoneRx.text.toString().trim()
        val signal = edtSignalThreshold.text.toString().toIntOrNull()
        val gps = edtGpsThreshold.text.toString().toIntOrNull()
        val timeout = edtTimeoutFactor.text.toString().toFloatOrNull()
        prefs.edit()
            .putBoolean(KEY_DEBUG_TRACK, switchDebugTrack.isChecked)
            .putBoolean("auto_mode_enabled", switchAutoMode.isChecked)
            .apply()

        var isValid = true

        if (signal == null || signal !in -110..-50) {
            edtSignalThreshold.error = "Valore ammesso: -110 a -50"
            isValid = false
        }

        if (gps == null || gps !in 1..100) {
            edtGpsThreshold.error = "Valore ammesso: 1 a 100"
            isValid = false
        }

        if (timeout == null || timeout <= 0f || timeout > 10f) {
            edtTimeoutFactor.error = "Valore ammesso: 0.1 a 10"
            isValid = false
        }

        if (phone.isBlank()) {
            edtPhoneRx.error = "Numero richiesto"
            isValid = false
        }

        if (!isValid) return

        prefs.edit()
            .putString(KEY_PHONE, phone)
            .putInt(KEY_SIGNAL, signal!!)
            .putInt(KEY_GPS, gps!!)
            .putFloat(KEY_TIMEOUT, timeout!!)
            .apply()

        Toast.makeText(this, "Impostazioni salvate", Toast.LENGTH_SHORT).show()

        finish()
    }
}