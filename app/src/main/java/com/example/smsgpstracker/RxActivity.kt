package com.example.smsgpstracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.gson.Gson
import java.io.File
import com.google.gson.reflect.TypeToken


class RxActivity : AppCompatActivity() {

    // UI
    private lateinit var txtStatus: TextView
    private lateinit var txtCount: TextView
    private lateinit var txtLast: TextView

    // Traccia GPS
    private val trackPoints = mutableListOf<GpsPoint>()

    // File
    private lateinit var trackFile: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rx)

        txtStatus = findViewById(R.id.txtStatus)
        txtCount = findViewById(R.id.txtCount)
        txtLast = findViewById(R.id.txtLast)

        initTrackFile()
        loadExistingTrack()

        txtStatus.text = "RX IN ATTESA"
    }

    override fun onStart() {
        super.onStart()
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(
                gpsReceiver,
                IntentFilter("GPS_POINT_RECEIVED")
            )
    }

    override fun onStop() {
        super.onStop()
        LocalBroadcastManager.getInstance(this)
            .unregisterReceiver(gpsReceiver)
    }

    // =========================
    // 📡 RICEZIONE GPS
    // =========================
    private val gpsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {

            val lat = intent?.getDoubleExtra("lat", 0.0) ?: return
            val lon = intent.getDoubleExtra("lon", 0.0)
            val time = intent.getLongExtra("time", 0)

            val point = GpsPoint(lat, lon, time)
            trackPoints.add(point)

            saveTrackToFile()

            txtStatus.text = "RX ATTIVO"
            txtCount.text = "Punti ricevuti: ${trackPoints.size}"
            txtLast.text = "Ultima posizione: $lat , $lon"
        }
    }

    // =========================
    // 💾 FILE
    // =========================
    private fun initTrackFile() {
        val dir = File(getExternalFilesDir(null), "tracks")
        if (!dir.exists()) dir.mkdirs()
        trackFile = File(dir, "track.json")
    }

    private fun saveTrackToFile() {
        val json = Gson().toJson(trackPoints)
        trackFile.writeText(json)
    }

    private fun loadExistingTrack() {
        if (!trackFile.exists()) return
        try {
            val json = trackFile.readText()
            val loaded = Gson().fromJson(json, Array<GpsPoint>::class.java)
            trackPoints.addAll(loaded)
            txtCount.text = "Punti ricevuti: ${trackPoints.size}"
        } catch (_: Exception) {
        }
    }
}
