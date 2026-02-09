package com.example.smsgpstracker

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.smsgpstracker.model.GpsTrackPoint
import com.example.smsgpstracker.repository.GpsTrackRepository
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class RxActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var txtStatus: TextView
    private lateinit var txtCount: TextView
    private lateinit var txtLast: TextView
    private lateinit var btnSaveSnapshot: Button

    private lateinit var googleMap: GoogleMap
    private val trackPoints = mutableListOf<GpsTrackPoint>()

    // opzionale: numero atteso SMS (se passato dal TX)
    private var expectedPoints: Int = -1
    private var snapshotDone = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rx)

        txtStatus = findViewById(R.id.txtStatus)
        txtCount = findViewById(R.id.txtCount)
        txtLast = findViewById(R.id.txtLast)
        btnSaveSnapshot = findViewById(R.id.btnSaveSnapshot)

        expectedPoints = intent.getIntExtra("EXPECTED_POINTS", -1)

        val mapFragment =
            supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        btnSaveSnapshot.setOnClickListener {
            saveMapSnapshot(manual = true)
        }

        loadFromDb()

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

            // 📸 Snapshot automatico a fine ciclo
            if (expectedPoints > 0 &&
                trackPoints.size == expectedPoints &&
                !snapshotDone
            ) {
                snapshotDone = true
                saveMapSnapshot(manual = false)
            }
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

    // =========================
    // 📸 SNAPSHOT MAPPA
    // =========================

    private fun saveMapSnapshot(manual: Boolean) {
        if (!::googleMap.isInitialized || trackPoints.isEmpty()) return

        googleMap.snapshot { bitmap ->
            if (bitmap == null) {
                if (manual) {
                    Toast.makeText(
                        this,
                        "Snapshot non disponibile",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return@snapshot
            }

            val overlayBitmap = drawOverlay(bitmap)
            saveBitmapToGallery(overlayBitmap)

            if (manual) {
                Toast.makeText(
                    this,
                    "Snapshot salvato in Galleria",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // 🎨 Overlay su bitmap
    private fun drawOverlay(source: Bitmap): Bitmap {
        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        val paintBg = Paint().apply {
            color = Color.argb(160, 0, 0, 0)
        }

        val paintText = Paint().apply {
            color = Color.WHITE
            textSize = 36f
            isAntiAlias = true
        }

        val padding = 20f

        val last = trackPoints.last()
        val lines = listOf(
            "Ultima posizione: ${last.latitude}, ${last.longitude}",
            "Punti ricevuti: ${trackPoints.size}",
            "Data: ${nowFormatted()}"
        )

        val boxHeight = lines.size * 45f + padding * 2

        canvas.drawRect(
            0f,
            result.height - boxHeight,
            result.width.toFloat(),
            result.height.toFloat(),
            paintBg
        )

        var y = result.height - boxHeight + padding + 35f
        lines.forEach {
            canvas.drawText(it, padding, y, paintText)
            y += 45f
        }

        return result
    }

    // 💾 Salvataggio JPG in DCIM/Camera
    private fun saveBitmapToGallery(bitmap: Bitmap) {
        val filename =
            "GpsTrack_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_DCIM + "/Camera"
            )
        }

        val uri = contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values
        ) ?: return

        contentResolver.openOutputStream(uri)?.use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)
        }
    }

    private fun nowFormatted(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date())
    }
}



