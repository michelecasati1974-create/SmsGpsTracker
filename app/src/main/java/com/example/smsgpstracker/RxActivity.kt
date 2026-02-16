package com.example.smsgpstracker

import android.annotation.SuppressLint
import android.content.*
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import java.net.URL
import android.widget.Switch
import android.location.Geocoder
import java.text.SimpleDateFormat
import java.util.*
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint


class RxActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var txtStatus: TextView
    private lateinit var txtCount: TextView
    private lateinit var txtLast: TextView
    private lateinit var googleMap: GoogleMap

    private var mapReady = false
    private var receiverRegistered = false

    private val trackPoints = mutableListOf<LatLng>()

    // =====================================================
    // BROADCAST RECEIVER (UNICO E CORRETTO)
    // =====================================================

    private val smsReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context?, intent: Intent?) {

            intent ?: return
            val msg = intent.getStringExtra("SMS_BODY") ?: return

            when (msg) {

                "CTRL:START" -> {

                    txtStatus.text = "Tracking avviato"
                    resetMapOnly()

                    val serviceIntent =
                        Intent(this@RxActivity, RxForegroundService::class.java)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        startForegroundService(serviceIntent)
                    else
                        startService(serviceIntent)
                }

                "CTRL:STOP",
                "CTRL:END" -> {

                    txtStatus.text = "Tracking completato"

                    val serviceIntent =
                        Intent(this@RxActivity, RxForegroundService::class.java)

                    serviceIntent.action = "END_TRACK"
                    startService(serviceIntent)

                    resetMapOnly()
                }

                "GPS" -> {

                    val lat = intent.getDoubleExtra("lat", 0.0)
                    val lon = intent.getDoubleExtra("lon", 0.0)

                    val point = LatLng(lat, lon)
                    trackPoints.add(point)

                    if (mapReady) {
                        drawAllPoints()
                    }

                    // Invia anche al Service
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

    private var cycloOverlay: TileOverlay? = null
    private var isCycloEnabled = false
    private fun enableCycloOverlay() {

        if (!mapReady) return
        if (cycloOverlay != null) return

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
                .fadeIn(true)
        )

        isCycloEnabled = true
    }

    private fun disableCycloOverlay() {
        cycloOverlay?.remove()
        cycloOverlay = null
        isCycloEnabled = false
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

        // STORAGE PERMISSION Android 7–9
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {

            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

                requestPermissions(
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    2001
                )
            }
        }

        // Register Receiver
        val filter = IntentFilter(SmsCommandProcessor.ACTION_SMS_EVENT)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                smsReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            registerReceiver(smsReceiver, filter)
        }

        receiverRegistered = true

        val switchMapType = findViewById<Switch>(R.id.switchMapType)

        switchMapType.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                enableCycloOverlay()
            } else {
                disableCycloOverlay()
            }
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        if (receiverRegistered) {
            unregisterReceiver(smsReceiver)
        }
    }

    @Suppress("MissingSuperCall")
    override fun onBackPressed() {
        moveTaskToBack(true)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 2001) {
            if (grantResults.isNotEmpty()
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                Toast.makeText(this, "Permesso storage concesso", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Permesso storage NEGATO", Toast.LENGTH_LONG).show()
            }
        }
    }

    // =====================================================
    // MAP READY
    // =====================================================

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        mapReady = true

        if (trackPoints.isNotEmpty()) {
            drawAllPoints()
        }
    }

    // =====================================================
    // DRAW MAP
    // =====================================================

    private var trackPolyline: Polyline? = null
    private var lastMarker: Marker? = null

    private fun drawAllPoints() {

        if (!mapReady || trackPoints.isEmpty()) return

        trackPolyline?.remove()
        lastMarker?.remove()

        // Disegna polilinea
        trackPolyline = googleMap.addPolyline(
            PolylineOptions()
                .addAll(trackPoints)
                .width(6f)
                .color(Color.BLACK)
        )

        val last = trackPoints.last()

        // 📅 DATA ORA
        val sdf = SimpleDateFormat(
            "dd/MM/yyyy HH:mm:ss",
            Locale.getDefault()
        )
        val dateTime = sdf.format(Date())

        // 📍 GEOCODER
        var country = ""
        var city = ""
        var province = ""

        try {
            val geocoder = Geocoder(this, Locale.getDefault())
            val addresses =
                geocoder.getFromLocation(
                    last.latitude,
                    last.longitude,
                    1
                )

            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                country = addr.countryName ?: ""
                city = addr.locality ?: ""
                province = addr.adminArea ?: ""
            }

        } catch (e: Exception) {}

        // 🔴 MARKER ROSSO CON INFO COMPLETE
        lastMarker = googleMap.addMarker(
            MarkerOptions()
                .position(last)
                .title("$city ($province)")
                .snippet("$country\n$dateTime")
                .icon(
                    BitmapDescriptorFactory.defaultMarker(
                        BitmapDescriptorFactory.HUE_RED
                    )
                )
        )

        lastMarker?.showInfoWindow()

        // 🔎 AUTO ZOOM SU TUTTI I PUNTI
        if (trackPoints.size > 1) {

            val builder = LatLngBounds.Builder()
            for (point in trackPoints) {
                builder.include(point)
            }

            val bounds = builder.build()

            googleMap.animateCamera(
                CameraUpdateFactory.newLatLngBounds(
                    bounds,
                    150   // padding
                )
            )

        } else {
            googleMap.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    last,
                    16f
                )
            )
        }

        txtCount.text = "Punti: ${trackPoints.size}"
        txtLast.text =
            "Ultima:\n${last.latitude}, ${last.longitude}"
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

