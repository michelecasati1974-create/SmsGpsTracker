package com.example.smsgpstracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.gson.Gson
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.graphics.*



class RxActivity : AppCompatActivity(), OnMapReadyCallback {

    private fun saveMapSnapshot(googleMap: GoogleMap) {

        googleMap.snapshot { bitmap ->

            if (bitmap == null) return@snapshot

            val mutableBitmap =
                bitmap.copy(Bitmap.Config.ARGB_8888, true)

            val canvas = Canvas(mutableBitmap)
            val paint = Paint().apply {
                color = Color.WHITE
                textSize = 36f
                isAntiAlias = true
                setShadowLayer(2f, 1f, 1f, Color.BLACK)
            }

            val last = trackPoints.lastOrNull() ?: return@snapshot
            val formattedTime =
                dateFormatter.format(java.util.Date(last.timestamp))

            val text =
                "Ultima posizione:\n" +
                        "${last.lat}, ${last.lon}\n" +
                        formattedTime

            var y = 50f
            text.split("\n").forEach {
                canvas.drawText(it, 20f, y, paint)
                y += 45f
            }

            saveBitmapToFile(mutableBitmap)
        }
    }

    private val dateFormatter =
        SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
    private lateinit var txtStatus: TextView
    private lateinit var txtCount: TextView
    private lateinit var txtLast: TextView

    private val trackPoints = mutableListOf<GpsPoint>()
    private lateinit var trackFile: File

    // MAPPA
    private lateinit var googleMap: GoogleMap
    private var polyline: Polyline? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rx)

        txtStatus = findViewById(R.id.txtStatus)
        txtCount = findViewById(R.id.txtCount)
        txtLast = findViewById(R.id.txtLast)

        initTrackFile()
        loadExistingTrack()

        txtStatus.text = "RX IN ATTESA"

        // MAPPA
        val mapFragment =
            supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // 📡 REGISTRA BROADCAST GPS
        registerReceiver(
            gpsReceiver,
            IntentFilter("com.example.smsgpstracker.GPS_POINT")
        )
    }
    private fun saveBitmapToFile(bitmap: Bitmap) {

        val dir = File(getExternalFilesDir(null), "snapshots")
        if (!dir.exists()) dir.mkdirs()

        val filename = "track_${System.currentTimeMillis()}.jpg"
        val file = File(dir, filename)

        file.outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(gpsReceiver)
    }

    // ======================
    // 🗺️ MAP READY
    // ======================
    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        redrawTrack()
    }

    // ======================
    // 📡 GPS RECEIVER
    // ======================
    private val gpsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {

            val lat = intent?.getDoubleExtra("lat", 0.0) ?: return
            val lon = intent.getDoubleExtra("lon", 0.0)
            val timestamp = System.currentTimeMillis()
            val point = GpsPoint(lat, lon, timestamp)
            trackPoints.add(point)

            saveTrackToFile()

            txtStatus.text = "RX ATTIVO"
            txtCount.text = "Punti ricevuti: ${trackPoints.size}"
            val formattedTime = dateFormatter.format(Date(timestamp))

            txtLast.text =
                "Ultima posizione:\n" +
                        "$lat , $lon\n" +
                        formattedTime

            redrawTrack()
        }
    }

    // ======================
    // 🧵 DISEGNO TRACCIA
    // ======================
    private fun redrawTrack() {
        if (!::googleMap.isInitialized || trackPoints.isEmpty()) return

        googleMap.clear()

        val latLngs = trackPoints.map {
            LatLng(it.lat, it.lon)
        }

        polyline = googleMap.addPolyline(
            PolylineOptions()
                .addAll(latLngs)
                .width(6f)
        )

        val last = latLngs.last()

        googleMap.addMarker(
            MarkerOptions()
                .position(last)
                .title("Ultima posizione")
        )

        googleMap.moveCamera(
            CameraUpdateFactory.newLatLngZoom(last, 16f)
        )
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
        if (trackPoints.isNotEmpty()) {
            val last = trackPoints.last()
            val formattedTime = dateFormatter.format(Date(last.timestamp))

            txtLast.text =
                "Ultima posizione:\n" +
                        "${last.lat} , ${last.lon}\n" +
                        formattedTime
        }
    }
}


