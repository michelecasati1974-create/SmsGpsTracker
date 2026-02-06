package com.example.smsgpstracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.smsgpstracker.repository.GpsTrackRepository
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import java.text.SimpleDateFormat
import java.util.*

class RxActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var googleMap: GoogleMap

    private lateinit var txtStatus: TextView
    private lateinit var txtCount: TextView
    private lateinit var txtLast: TextView

    private val dateFormatter =
        SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())

    private val rxReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != SmsCommandProcessor.ACTION_NEW_POSITION) return
            updateTextUi()
            redrawMap()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rx)

        txtStatus = findViewById(R.id.txtStatus)
        txtCount = findViewById(R.id.txtCount)
        txtLast = findViewById(R.id.txtLast)

        val mapFragment =
            supportFragmentManager.findFragmentById(R.id.map)
                    as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onStart() {
        super.onStart()

        val filter =
            IntentFilter(SmsCommandProcessor.ACTION_NEW_POSITION)

        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(
                rxReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            registerReceiver(rxReceiver, filter)
        }

        updateTextUi()
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(rxReceiver)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        redrawMap()
    }

    private fun redrawMap() {
        if (!::googleMap.isInitialized) return

        googleMap.clear()

        val polylineOptions = PolylineOptions().width(6f)
        val points = GpsTrackRepository.getAllPoints()

        points.forEachIndexed { index, p ->
            val latLng = LatLng(p.latitude, p.longitude)

            googleMap.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title("Punto ${index + 1}")
            )

            polylineOptions.add(latLng)
        }

        if (points.isNotEmpty()) {
            googleMap.addPolyline(polylineOptions)

            val last = points.last()
            googleMap.moveCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(last.latitude, last.longitude),
                    15f
                )
            )
        }
    }

    private fun updateTextUi() {

        txtStatus.text = "RX ATTIVO"
        txtCount.text =
            "Punti ricevuti: ${GpsTrackRepository.size()}"

        val last = GpsTrackRepository.lastPoint()

        txtLast.text =
            if (last != null) {
                "Ultima posizione:\n" +
                        "${last.latitude}, ${last.longitude}\n" +
                        dateFormatter.format(Date(last.timestamp))
            } else {
                "Ultima posizione: --"
            }
    }
}
