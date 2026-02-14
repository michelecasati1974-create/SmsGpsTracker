package com.example.smsgpstracker

import android.annotation.SuppressLint
import android.content.*
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Environment
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.CameraUpdateFactory
import kotlinx.coroutines.delay
import androidx.lifecycle.lifecycleScope

class RxActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var txtStatus: TextView
    private lateinit var txtCount: TextView
    private lateinit var txtLast: TextView
    private lateinit var googleMap: GoogleMap

    private var mapReady = false
    private val trackPoints = mutableListOf<LatLng>()

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

        val filter =
            IntentFilter(SmsCommandProcessor.ACTION_SMS_EVENT)

        if (android.os.Build.VERSION.SDK_INT >= 33) {
            registerReceiver(
                smsReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            registerReceiver(smsReceiver, filter)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(smsReceiver)
    }

    // =====================================================
    // MAP READY
    // =====================================================

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        mapReady = true

        // Se ci sono punti accumulati prima che la mappa fosse pronta
        if (trackPoints.isNotEmpty()) {
            drawAllPoints()
        }
    }

    // =====================================================
    // BROADCAST RECEIVER
    // =====================================================

    private val smsReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context?, intent: Intent?) {

            intent ?: return
            val msg = intent.getStringExtra("SMS_BODY") ?: return

            when (msg) {

                "CTRL:START" -> {
                    txtStatus.text = "Tracking avviato"
                    resetMapOnly()
                }

                "CTRL:STOP" -> {
                    txtStatus.text = "Tracking fermato"
                    completeTracking()
                }

                "CTRL:END" -> {
                    txtStatus.text = "Tracking completato"
                    completeTracking()
                }

                "GPS" -> {

                    val lat =
                        intent.getDoubleExtra("lat", 0.0)
                    val lon =
                        intent.getDoubleExtra("lon", 0.0)

                    val point = LatLng(lat, lon)

                    trackPoints.add(point)

                    if (mapReady) {
                        drawAllPoints()
                    }
                }
            }
        }
    }

    private fun completeTracking() {

        if (!mapReady || trackPoints.isEmpty()) return

        lifecycleScope.launch {

            delay(600)

            try {

                val bounds = LatLngBounds.Builder().apply {
                    trackPoints.forEach { include(it) }
                }.build()

                googleMap.animateCamera(
                    CameraUpdateFactory.newLatLngBounds(bounds, 150)
                )

            } catch (e: Exception) {

                googleMap.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        trackPoints.last(),
                        16f
                    )
                )
            }

            delay(800)

            saveSnapshotThenReset()
        }
    }

    // =====================================================
    // DRAW MAP
    // =====================================================

    private fun drawAllPoints() {

        if (!mapReady) return

        googleMap.clear()

        googleMap.addPolyline(
            PolylineOptions()
                .addAll(trackPoints)
                .width(6f)
        )

        val last = trackPoints.last()

        googleMap.addMarker(
            MarkerOptions().position(last)
        )

        googleMap.animateCamera(
            CameraUpdateFactory.newLatLngZoom(last, 16f)
        )

        txtCount.text =
            "Punti: ${trackPoints.size}"

        txtLast.text =
            "Ultima:\n${last.latitude}, ${last.longitude}"
    }

    // =====================================================
    // SNAPSHOT
    // =====================================================

    private fun saveSnapshotThenReset() {

        if (!mapReady || trackPoints.isEmpty()) return

        val last = trackPoints.last()

        lifecycleScope.launch {

            val address =
                getAddressText(
                    last.latitude,
                    last.longitude
                )

            googleMap.snapshot { bitmap ->

                if (bitmap != null) {

                    saveBitmapWithText(
                        bitmap,
                        address
                    )

                    Toast.makeText(
                        this@RxActivity,
                        "Snapshot salvato",
                        Toast.LENGTH_LONG
                    ).show()

                    resetMapOnly()
                }
            }
        }
    }

    private suspend fun getAddressText(
        lat: Double,
        lon: Double
    ): String {

        return try {

            val geocoder =
                android.location.Geocoder(
                    this,
                    Locale.getDefault()
                )

            val list =
                geocoder.getFromLocation(lat, lon, 1)

            if (!list.isNullOrEmpty()) {

                val addr = list[0]
                val comune = addr.locality ?: ""
                val provincia = addr.adminArea ?: ""

                "$comune ($provincia)"
            } else "Località sconosciuta"

        } catch (e: Exception) {
            "Località sconosciuta"
        }
    }

    private fun saveBitmapWithText(
        bitmap: Bitmap,
        address: String
    ) {

        lifecycleScope.launch(Dispatchers.IO) {

            val mutable =
                bitmap.copy(Bitmap.Config.ARGB_8888, true)

            val canvas =
                android.graphics.Canvas(mutable)

            val paint =
                android.graphics.Paint().apply {
                    color = android.graphics.Color.RED
                    textSize = 60f
                    isAntiAlias = true
                }

            canvas.drawText(
                address,
                50f,
                80f,
                paint
            )

            val folder =
                getExternalFilesDir(
                    Environment.DIRECTORY_PICTURES
                )

            val time =
                SimpleDateFormat(
                    "yyyyMMdd_HHmmss",
                    Locale.getDefault()
                ).format(Date())

            val file =
                File(folder, "TRACK_$time.jpg")

            FileOutputStream(file).use {
                mutable.compress(
                    Bitmap.CompressFormat.JPEG,
                    95,
                    it
                )
            }
        }
    }

    // =====================================================
    // RESET
    // =====================================================

    private fun resetMapOnly() {

        trackPoints.clear()

        if (mapReady)
            googleMap.clear()

        txtCount.text = "Punti: 0"
        txtLast.text = "Ultima: --"
    }
}







