package com.example.smsgpstracker

import android.annotation.SuppressLint
import android.content.*
import android.graphics.Color
import android.os.Build
import android.os.Bundle
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
                }

                "CTRL:STOP", "CTRL:END" -> {
                    txtStatus.text = "Tracking completato"

                    if (trackPoints.isNotEmpty()) {

                        val serviceIntent =
                            Intent(this@RxActivity, RxExportForegroundService::class.java)

                        serviceIntent.putParcelableArrayListExtra(
                            "TRACK_POINTS",
                            ArrayList(trackPoints)
                        )

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(serviceIntent)
                        } else {
                            startService(serviceIntent)
                        }
                    }
                }

                "GPS" -> {

                    val lat = intent.getDoubleExtra("lat", 0.0)
                    val lon = intent.getDoubleExtra("lon", 0.0)

                    val point = LatLng(lat, lon)
                    trackPoints.add(point)

                    if (mapReady) {
                        drawAllPoints()
                    }
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

        txtStatus = findViewById(R.id.txtStatus)
        txtCount = findViewById(R.id.txtCount)
        txtLast = findViewById(R.id.txtLast)

        val mapFragment =
            supportFragmentManager.findFragmentById(R.id.map)
                    as SupportMapFragment

        mapFragment.getMapAsync(this)

        val filter = IntentFilter(SmsCommandProcessor.ACTION_SMS_EVENT)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(smsReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(smsReceiver, filter)
        }

        receiverRegistered = true

        val switchMapType = findViewById<Switch>(R.id.switchMapType)

        switchMapType.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) enableCycloOverlay()
            else disableCycloOverlay()
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

        if (trackPoints.isNotEmpty()) {
            drawAllPoints()
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

        if (trackPoints.size > 1) {

            val builder = LatLngBounds.Builder()
            trackPoints.forEach { builder.include(it) }

            googleMap.animateCamera(
                CameraUpdateFactory.newLatLngBounds(builder.build(), 150)
            )

        } else {
            googleMap.animateCamera(
                CameraUpdateFactory.newLatLngZoom(last, 16f)
            )
        }

        txtCount.text = "Punti: ${trackPoints.size}"
        txtLast.text = "Ultima:\n${last.latitude}, ${last.longitude}"
    }

    // =====================================================
    // CYCLOSM
    // =====================================================

    private fun enableCycloOverlay() {

        if (!mapReady || cycloOverlay != null) return

        val tileProvider = object : UrlTileProvider(256, 256) {
            override fun getTileUrl(x: Int, y: Int, zoom: Int): URL? {
                return try {
                    URL("https://a.tile-cyclosm.openstreetmap.fr/cyclosm/$zoom/$x/$y.png")
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

    private fun disableCycloOverlay() {
        cycloOverlay?.remove()
        cycloOverlay = null
        isCycloEnabled = false
    }

    // =====================================================
    // RESET
    // =====================================================

    private fun resetMapOnly() {

        trackPoints.clear()
        trackPolyline?.remove()
        lastMarker?.remove()

        txtCount.text = "Punti: 0"
        txtLast.text = "Ultima: --"
    }
}
