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

class RxActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var txtStatus: TextView
    private lateinit var txtCount: TextView
    private lateinit var txtLast: TextView
    private lateinit var googleMap: GoogleMap

    private val trackPoints = mutableListOf<LatLng>()

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

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
    }

    // ================= BROADCAST =================

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
                    saveSnapshotThenReset()
                }

                "CTRL:END" -> {
                    txtStatus.text = "Tracking completato"
                    saveSnapshotThenReset()
                }

                "GPS" -> {

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

        googleMap.clear()

        googleMap.addPolyline(
            PolylineOptions()
                .addAll(trackPoints)
                .width(6f)
        )

        googleMap.addMarker(
            MarkerOptions().position(point)
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

                Toast.makeText(
                    this,
                    "Snapshot salvato",
                    Toast.LENGTH_LONG
                ).show()

                resetMapOnly()
            }
        }
    }

    private fun saveBitmap(bitmap: Bitmap) {

        lifecycleScope.launch(Dispatchers.IO) {

            val folder =
                getExternalFilesDir(Environment.DIRECTORY_PICTURES)

            val time =
                SimpleDateFormat(
                    "yyyyMMdd_HHmmss",
                    Locale.getDefault()
                ).format(Date())

            val file =
                File(folder, "TRACK_$time.jpg")

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






