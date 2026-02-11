package com.example.smsgpstracker

import android.graphics.Bitmap
import android.os.Bundle
import android.os.Environment
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent


class RxActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var txtStatus: TextView
    private lateinit var txtCount: TextView
    private lateinit var txtLast: TextView
    private lateinit var googleMap: GoogleMap

    private val trackPoints = mutableListOf<LatLng>()

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

        ContextCompat.registerReceiver(
            this,
            smsReceiver,
            android.content.IntentFilter(
                SmsCommandProcessor.ACTION_SMS_EVENT
            ),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(smsReceiver)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
    }

    // ================= BROADCAST =================

    private val smsReceiver: BroadcastReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context?, intent: Intent?) {

            if (intent == null) return

            val msg = intent.getStringExtra("SMS_BODY") ?: return

            when {

                msg.equals("CTRL:START", true) -> {
                    resetMapOnly()
                }

                msg.equals("CTRL:END", true) ||
                        msg.equals("CTRL:STOP", true) -> {
                    saveSnapshotThenReset()
                }

                msg == "GPS" -> {

                    val lat = intent.getDoubleExtra("lat", 0.0)
                    val lon = intent.getDoubleExtra("lon", 0.0)

                    val point = LatLng(lat, lon)
                    trackPoints.add(point)

                    updateMapRealtime(point)
                }
            }
        }
    }

    // ================= REALTIME =================

    private fun updateMapRealtime(point: LatLng) {

        if (!::googleMap.isInitialized) return

        googleMap.addMarker(
            MarkerOptions().position(point)
        )

        googleMap.addPolyline(
            PolylineOptions()
                .addAll(trackPoints)
                .width(6f)
        )

        googleMap.animateCamera(
            CameraUpdateFactory.newLatLngZoom(point, 16f)
        )

        txtCount.text = "Punti: ${trackPoints.size}"
        txtLast.text = "Ultima:\n${point.latitude}, ${point.longitude}"
    }

    // ================= SNAPSHOT =================

    private fun saveSnapshotThenReset() {

        if (!::googleMap.isInitialized) return

        googleMap.snapshot { bitmap ->

            if (bitmap != null) {
                saveBitmap(bitmap)
                resetMapOnly()

                Toast.makeText(
                    this,
                    "Ciclo terminato e salvato",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun saveBitmap(bitmap: Bitmap) {

        lifecycleScope.launch(Dispatchers.IO) {

            val folder = getExternalFilesDir(
                Environment.DIRECTORY_PICTURES
            )

            val time =
                SimpleDateFormat(
                    "yyyyMMdd_HHmmss",
                    Locale.getDefault()
                ).format(Date())

            val file = File(
                folder,
                "TRACK_$time.jpg"
            )

            FileOutputStream(file).use {
                bitmap.compress(
                    Bitmap.CompressFormat.JPEG,
                    95,
                    it
                )
            }
        }
    }

    // ================= RESET =================

    private fun resetMapOnly() {

        trackPoints.clear()

        if (::googleMap.isInitialized)
            googleMap.clear()

        txtCount.text = "Punti: 0"
        txtLast.text = "Ultima: --"
    }

}






