package com.example.smsgpstracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.smsgpstracker.model.GpsTrackPoint
import com.example.smsgpstracker.repository.GpsTrackRepository
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RxActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var txtStatus: TextView
    private lateinit var txtCount: TextView
    private lateinit var txtLast: TextView

    private lateinit var googleMap: GoogleMap
    private val trackPoints = mutableListOf<GpsTrackPoint>()

    // ==========================================================
    // LIFECYCLE
    // ==========================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rx)

        txtStatus = findViewById(R.id.txtStatus)
        txtCount = findViewById(R.id.txtCount)
        txtLast = findViewById(R.id.txtLast)

        val mapFragment =
            supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        registerReceiver(
            smsReceiver,
            IntentFilter(SmsCommandProcessor.ACTION_SMS_EVENT)
        )

        loadFromDb()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(smsReceiver)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        redrawTrack()
    }

    // ==========================================================
    // 📡 BROADCAST RECEIVER
    // ==========================================================

    private val smsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {

            val msg = intent?.getStringExtra("SMS_BODY")
                ?.replace("\n", "")
                ?.trim()
                ?: return

            Log.d("RX_UI", "Broadcast ricevuto: $msg")

            when {

                msg.equals("CTRL:START", true) -> {
                    Log.d("RX_UI", "CTRL START ricevuto")
                    resetAll()
                }

                msg.equals("CTRL:END", true) -> {
                    Log.d("RX_UI", "CTRL END ricevuto")
                    resetAll()
                    Toast.makeText(
                        this@RxActivity,
                        "Ciclo terminato (END)",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                msg.equals("CTRL:STOP", true) -> {
                    Log.d("RX_UI", "CTRL STOP ricevuto")
                    resetAll()
                    Toast.makeText(
                        this@RxActivity,
                        "Ciclo terminato (STOP)",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                msg.startsWith("GPS:", true) -> {
                    Log.d("RX_UI", "GPS ricevuto → aggiorno mappa")
                    loadFromDb()
                }
            }
        }
    }

    // ==========================================================
    // RESET COMPLETO CICLO
    // ==========================================================

    private fun resetAll() {

        lifecycleScope.launch(Dispatchers.IO) {
            GpsTrackRepository.clear(this@RxActivity)

            launch(Dispatchers.Main) {
                trackPoints.clear()

                if (::googleMap.isInitialized) {
                    googleMap.clear()
                }

                updateText()

                Log.d("RX_UI", "Reset completo eseguito")
            }
        }
    }

    // ==========================================================
    // CARICAMENTO DATABASE
    // ==========================================================

    private fun loadFromDb() {

        lifecycleScope.launch(Dispatchers.IO) {

            val points =
                GpsTrackRepository.getAll(this@RxActivity)

            launch(Dispatchers.Main) {

                trackPoints.clear()
                trackPoints.addAll(points)

                redrawTrack()
                updateText()

                Log.d("RX_UI", "Mappa aggiornata. Punti: ${trackPoints.size}")
            }
        }
    }

    // ==========================================================
    // DISEGNO MAPPA
    // ==========================================================

    private fun redrawTrack() {

        if (!::googleMap.isInitialized) return

        googleMap.clear()

        if (trackPoints.isEmpty()) return

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

    // ==========================================================
    // AGGIORNAMENTO UI
    // ==========================================================

    private fun updateText() {

        txtStatus.text = "RX ATTIVO"
        txtCount.text = "Punti ricevuti: ${trackPoints.size}"

        trackPoints.lastOrNull()?.let {
            txtLast.text =
                "Ultima posizione:\n${it.latitude}, ${it.longitude}"
        } ?: run {
            txtLast.text = "Ultima posizione: --"
        }
    }
}



