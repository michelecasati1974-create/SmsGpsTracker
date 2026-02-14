package com.example.smsgpstracker

import android.annotation.SuppressLint
import android.content.*
import android.graphics.*
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class RxActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var txtStatus: TextView
    private lateinit var txtCount: TextView
    private lateinit var txtLast: TextView
    private lateinit var googleMap: GoogleMap

    private var mapReady = false
    private var receiverRegistered = false
    private var snapshotInProgress = false

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

        val filter = IntentFilter(SmsCommandProcessor.ACTION_SMS_EVENT)

        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(
                smsReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            registerReceiver(smsReceiver, filter)
        }

        receiverRegistered = true
    }

    override fun onDestroy() {
        super.onDestroy()
        if (receiverRegistered) {
            unregisterReceiver(smsReceiver)
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

                "CTRL:STOP",
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

    // =====================================================
    // COMPLETE TRACKING
    // =====================================================

    private fun completeTracking() {

        if (!mapReady || trackPoints.isEmpty()) return
        if (snapshotInProgress) return

        snapshotInProgress = true

        lifecycleScope.launch {

            delay(500)

            try {

                if (trackPoints.size > 1) {

                    val bounds = LatLngBounds.Builder().apply {
                        trackPoints.forEach { include(it) }
                    }.build()

                    googleMap.animateCamera(
                        CameraUpdateFactory.newLatLngBounds(bounds, 150)
                    )
                } else {

                    googleMap.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(
                            trackPoints.last(),
                            16f
                        )
                    )
                }

            } catch (_: Exception) {}

            delay(700)

            saveSnapshotThenReset()
        }
    }

    // =====================================================
    // DRAW MAP
    // =====================================================

    private fun drawAllPoints() {

        if (!mapReady || trackPoints.isEmpty()) return

        googleMap.clear()

        googleMap.addPolyline(
            PolylineOptions()
                .addAll(trackPoints)
                .width(6f)
                .color(Color.BLACK)
        )

        val last = trackPoints.last()

        googleMap.addMarker(
            MarkerOptions()
                .position(last)
                .title("Ultima posizione")
        )

        txtCount.text = "Punti: ${trackPoints.size}"
        txtLast.text =
            "Ultima:\n${last.latitude}, ${last.longitude}"
    }

    // =====================================================
    // SNAPSHOT
    // =====================================================

    private fun saveSnapshotThenReset() {

        if (trackPoints.isEmpty()) {
            snapshotInProgress = false
            return
        }

        // Assicurati che polilinee e marker siano già disegnati
        drawAllPoints()

        googleMap.setOnMapLoadedCallback {

            googleMap.snapshot { mapBitmap ->

                if (mapBitmap == null) {
                    snapshotInProgress = false
                    return@snapshot
                }

                lifecycleScope.launch(Dispatchers.IO) {

                    val bitmap = mapBitmap.copy(
                        Bitmap.Config.ARGB_8888,
                        true
                    )

                    val canvas = Canvas(bitmap)

                    val paintText = Paint().apply {
                        color = Color.BLACK
                        textSize = 40f
                        isAntiAlias = true
                        typeface = Typeface.DEFAULT_BOLD
                    }

                    val last = trackPoints.last()

                    val address =
                        getAddressText(last.latitude, last.longitude)

                    canvas.drawText(address, 60f, 70f, paintText)

                    paintText.textSize = 34f
                    paintText.typeface = Typeface.DEFAULT

                    canvas.drawText(
                        "Lat: ${last.latitude}",
                        60f,
                        120f,
                        paintText
                    )

                    canvas.drawText(
                        "Lon: ${last.longitude}",
                        60f,
                        160f,
                        paintText
                    )

                    val time =
                        SimpleDateFormat(
                            "yyyyMMdd_HHmmss",
                            Locale.getDefault()
                        ).format(Date())

                    val values = ContentValues().apply {
                        put(
                            MediaStore.Images.Media.DISPLAY_NAME,
                            "TRACK_$time.jpg"
                        )
                        put(
                            MediaStore.Images.Media.MIME_TYPE,
                            "image/jpeg"
                        )
                        put(
                            MediaStore.Images.Media.RELATIVE_PATH,
                            Environment.DIRECTORY_PICTURES + "/SmsGpsTracker"
                        )
                    }

                    val uri = contentResolver.insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        values
                    )

                    uri?.let {
                        contentResolver.openOutputStream(it)?.use { out ->
                            bitmap.compress(
                                Bitmap.CompressFormat.JPEG,
                                95,
                                out
                            )
                        }
                    }

                    launch(Dispatchers.Main) {
                        resetMapOnly()
                        snapshotInProgress = false
                    }
                }
            }
        }
    }


    private fun saveBitmapWithText(
        bitmap: Bitmap,
        address: String
    ) {

        lifecycleScope.launch(Dispatchers.IO) {

            val mutable =
                bitmap.copy(Bitmap.Config.ARGB_8888, true)

            val canvas = Canvas(mutable)

            val paint = Paint().apply {
                color = Color.BLACK
                textSize = 40f
                isAntiAlias = true
                typeface = Typeface.DEFAULT_BOLD
            }

            val last = trackPoints.last()

            canvas.drawText(
                address,
                40f,
                70f,
                paint
            )

            paint.textSize = 34f
            paint.typeface = Typeface.DEFAULT

            canvas.drawText(
                "Lat: ${last.latitude}",
                40f,
                115f,
                paint
            )

            canvas.drawText(
                "Lon: ${last.longitude}",
                40f,
                155f,
                paint
            )

            val time =
                SimpleDateFormat(
                    "yyyyMMdd_HHmmss",
                    Locale.getDefault()
                ).format(Date())

            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "TRACK_$time.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/SmsGpsTracker"
                )
            }

            val uri = contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values
            )

            uri?.let {
                contentResolver.openOutputStream(it)?.use { out ->
                    mutable.compress(
                        Bitmap.CompressFormat.JPEG,
                        95,
                        out
                    )
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
                Geocoder(this, Locale.getDefault())

            val list =
                geocoder.getFromLocation(lat, lon, 1)

            if (!list.isNullOrEmpty()) {

                val addr = list[0]
                val comune = addr.locality ?: ""
                val provincia = addr.adminArea ?: ""

                "$comune ($provincia)"
            } else "Località sconosciuta"

        } catch (_: Exception) {
            "Località sconosciuta"
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
