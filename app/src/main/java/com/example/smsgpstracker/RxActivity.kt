package com.example.smsgpstracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.smsgpstracker.model.GpsTrackPoint
import com.example.smsgpstracker.repository.GpsTrackRepository
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import kotlinx.coroutines.launch
import androidx.core.content.ContextCompat

class RxActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var txtStatus: TextView
    private lateinit var txtCount: TextView
    private lateinit var txtLast: TextView

    private lateinit var googleMap: GoogleMap
    private val trackPoints = mutableListOf<GpsTrackPoint>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rx)

        txtStatus = findViewById(R.id.txtStatus)
        txtCount = findViewById(R.id.txtCount)
        txtLast = findViewById(R.id.txtLast)

        val mapFragment =
            supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // 🔄 Caricamento iniziale DB (COROUTINE)
        loadFromDb()

        // 📡 REGISTRA RECEIVER (Android 7+ safe)
        val filter = IntentFilter(SmsCommandProcessor.ACTION_NEW_POSITION)

        ContextCompat.registerReceiver(
            this,
            gpsReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(gpsReceiver)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        redrawTrack()
    }

    // 📥 DB → memoria
    private fun loadFromDb() {
        lifecycleScope.launch {
            trackPoints.clear()
            trackPoints.addAll(GpsTrackRepository.getAll(this@RxActivity))
            updateText()
            redrawTrack()
        }
    }

    // 📡 Ricezione nuovo punto
    private val gpsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loadFromDb()
        }
    }

    // 🗺️ Disegno mappa
    private fun redrawTrack() {
        if (!::googleMap.isInitialized || trackPoints.isEmpty()) return

        googleMap.clear()

        val latLngs = trackPoints.map {
            LatLng(it.latitude, it.longitude)
        }

        googleMap.addPolyline(
            PolylineOptions()
                .addAll(latLngs)
                .width(6f)
        )

        latLngs.forEach {
            googleMap.addMarker(MarkerOptions().position(it))
        }

        googleMap.moveCamera(
            CameraUpdateFactory.newLatLngZoom(
                latLngs.last(),
                16f
            )
        )
    }

    // 🧾 UI
    private fun updateText() {
        txtStatus.text = "RX ATTIVO"
        txtCount.text = "Punti ricevuti: ${trackPoints.size}"

        trackPoints.lastOrNull()?.let {
            txtLast.text = "Ultima posizione:\n${it.latitude}, ${it.longitude}"
        } ?: run {
            txtLast.text = "Ultima posizione: --"
        }
    }
}



