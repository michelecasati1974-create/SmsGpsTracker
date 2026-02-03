package com.example.smsgpstracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import java.io.File

class RxActivity : AppCompatActivity() {

    private lateinit var txtStatus: TextView
    private lateinit var txtCount: TextView
    private lateinit var txtLast: TextView

    private val trackPoints = mutableListOf<GpsPoint>()
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

        // 📡 REGISTRA BROADCAST GPS
        registerReceiver(
            gpsReceiver,
            IntentFilter("com.example.smsgpstracker.GPS_POINT")
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(gpsReceiver)
    }

    // ======================
    // 📡 GPS RECEIVER
    // ======================
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

    // ======================
    // 💾 FILE
    // ======================
    private fun initTrackFile() {
        val dir = File(getExternalFilesDir(null), "tracks")
        if (!dir.exists()) dir.mkdirs()
        trackFile = File(dir, "track.json")
    }

    private fun saveTrackToFile() {
        trackFile.writeText(Gson().toJson(trackPoints))
    }

    private fun loadExistingTrack() {
        if (!trackFile.exists()) return
        val json = trackFile.readText()
        val loaded = Gson().fromJson(json, Array<GpsPoint>::class.java)
        trackPoints.addAll(loaded)
        txtCount.text = "Punti ricevuti: ${trackPoints.size}"
    }
}


