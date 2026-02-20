package com.example.smsgpstracker

import android.annotation.SuppressLint
import android.content.*
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import java.net.URL

class RxActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var txtStatus: TextView
    private lateinit var txtCount: TextView
    private lateinit var txtLast: TextView
    private lateinit var googleMap: GoogleMap

    private var mapReady = false
    private var receiverRegistered = false

    private val trackPoints = mutableListOf<LatLng>()

    private var trackPolyline: Polyline? = null
    private var lastMarker: Marker? = null

    private var cycloOverlay: TileOverlay? = null
    private var isCycloEnabled = false

    private lateinit var prefs: SharedPreferences
    private var selectedMapProvider = "GOOGLE"   // GOOGLE o MAPTILER


    // =====================================================
    // RECEIVER SMS
    // =====================================================
    private val smsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val msg = intent?.getStringExtra("SMS_BODY") ?: return

            when (msg) {

                "CTRL:START" -> {
                    txtStatus.text = "Tracking avviato"
                    resetMapOnly()

                    // START servizio tracking interno
                    val serviceIntent =
                        Intent(this@RxActivity, RxForegroundService::class.java)
                    serviceIntent.action = "START_TRACK"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(serviceIntent)
                    } else {
                        startService(serviceIntent)
                    }

                    // Mostra subito mappa e overlay CyclOSM se attivo
                    if (mapReady) {
                        googleMap.mapType = GoogleMap.MAP_TYPE_NORMAL
                        if (isCycloEnabled) {
                            cycloOverlay?.remove()
                            enableMapTilerOverlay()
                        }
                    }
                }

                "CTRL:STOP", "CTRL:END" -> {
                    txtStatus.text = "Tracking completato"

                    val serviceIntent =
                        Intent(this@RxActivity, RxForegroundService::class.java)
                    serviceIntent.action = "END_TRACK"
                    startService(serviceIntent)
                }

                "GPS" -> {
                    val lat = intent.getDoubleExtra("lat", 0.0)
                    val lon = intent.getDoubleExtra("lon", 0.0)
                    val point = LatLng(lat, lon)
                    trackPoints.add(point)

                    if (mapReady) {
                        drawAllPoints()
                    }

                    // Invia anche al servizio RxForegroundService
                    val serviceIntent =
                        Intent(this@RxActivity, RxForegroundService::class.java)
                    serviceIntent.action = "ADD_POINT"
                    serviceIntent.putExtra("lat", lat)
                    serviceIntent.putExtra("lon", lon)
                    startService(serviceIntent)
                }
            }
        }
    }

    // =====================================================
    // ON CREATE
    // =====================================================
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rx)
        prefs = getSharedPreferences("map_settings", MODE_PRIVATE)
        selectedMapProvider = prefs.getString("provider", "GOOGLE")!!

        txtStatus = findViewById(R.id.txtStatus)
        txtCount = findViewById(R.id.txtCount)
        txtLast = findViewById(R.id.txtLast)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        val filter = IntentFilter(SmsCommandProcessor.ACTION_SMS_EVENT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(smsReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(smsReceiver, filter)
        }
        receiverRegistered = true

        val switchMapType = findViewById<Switch>(R.id.switchMapType)

        switchMapType.isChecked = selectedMapProvider == "MAPTILER"

        switchMapType.setOnCheckedChangeListener { _, isChecked ->

            selectedMapProvider = if (isChecked) "MAPTILER" else "GOOGLE"

            prefs.edit().putString("provider", selectedMapProvider).apply()

            if (isChecked) enableMapTilerOverlay()
            else disableMapTilerOverlay()
        }

        val btnExport = findViewById<Button>(R.id.btnExport)
        btnExport.setOnClickListener {
            if (trackPoints.isNotEmpty()) {
                val serviceIntent = Intent(this, RxExportForegroundService::class.java)
                serviceIntent.putParcelableArrayListExtra("TRACK_POINTS", ArrayList(trackPoints))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (receiverRegistered) unregisterReceiver(smsReceiver)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        mapReady = true
        googleMap.mapType = GoogleMap.MAP_TYPE_NORMAL

        // Se ci sono punti già ricevuti, ridisegnali
        if (trackPoints.isNotEmpty()) drawAllPoints()
        // Ricarica overlay CyclOSM se abilitato
        if (selectedMapProvider == "MAPTILER") {
            enableMapTilerOverlay()
        }
        if (selectedMapProvider == "MAPTILER") {
            enableMapTilerOverlay()
        }
    }

    // =====================================================
    // DRAW TRACK
    // =====================================================
    private fun drawAllPoints() {
        if (!mapReady || trackPoints.isEmpty()) return

        trackPolyline?.remove()
        lastMarker?.remove()

        trackPolyline = googleMap.addPolyline(
            PolylineOptions()
                .addAll(trackPoints)
                .width(6f)
                .color(Color.BLACK)
        )

        val last = trackPoints.last()
        lastMarker = googleMap.addMarker(
            MarkerOptions()
                .position(last)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
        )

        val builder = LatLngBounds.Builder()
        trackPoints.forEach { builder.include(it) }
        googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 150))

        txtCount.text = "Punti: ${trackPoints.size}"
        txtLast.text = "Ultima:\n${last.latitude}, ${last.longitude}"
    }



    // =====================================================
    // CYCLOSM
    // =====================================================
    private fun enableMapTilerOverlay() {

        if (!mapReady) return

        disableMapTilerOverlay()

        val apiKey = "evudqvofcpXRhzwIFeFO"

        val tileProvider = object : UrlTileProvider(256, 256) {
            override fun getTileUrl(x: Int, y: Int, zoom: Int): URL? {
                return try {
                    URL("https://api.maptiler.com/maps/topo-v2/256/$zoom/$x/$y.png?key=$apiKey")
                } catch (e: Exception) {
                    null
                }
            }
        }

        cycloOverlay = googleMap.addTileOverlay(
            TileOverlayOptions()
                .tileProvider(tileProvider)
                .fadeIn(false)
        )

        isCycloEnabled = true
    }

    private fun disableMapTilerOverlay() {
        cycloOverlay?.remove()
        cycloOverlay = null
        isCycloEnabled = false
    }

    // =====================================================
    // RESET MAP
    // =====================================================
    private fun resetMapOnly() {
        trackPoints.clear()
        trackPolyline?.remove()
        lastMarker?.remove()

        // NON cancellare Google tiles
        googleMap.mapType = GoogleMap.MAP_TYPE_NORMAL

        // Ricarica overlay CyclOSM se era attivo
        if (selectedMapProvider == "MAPTILER") {
            enableMapTilerOverlay()
        }

        // Centro iniziale (Italia)
        val defaultLocation = LatLng(41.9028, 12.4964) // Roma
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 5f))

        txtCount.text = "Punti: 0"
        txtLast.text = "Ultima: --"
    }
}


