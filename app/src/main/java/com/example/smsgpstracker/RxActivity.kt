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
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.location.Geocoder
import android.os.Environment
import android.provider.MediaStore
import android.content.ContentValues
import android.content.ContentUris
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.os.Handler
import android.os.Looper
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLngBounds
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import android.util.Log
import com.example.smsgpstracker.rxmulti.RxMultiSmsParser
import com.example.smsgpstracker.rxmulti.RxMultiTrackAssembler


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

    private val manualPoints = mutableListOf<LatLng>()

    private val rxAssembler = RxTrackAssembler()








    // =====================================================
    // RECEIVER SMS
    // =====================================================
    private val smsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val msg = intent?.getStringExtra("SMS_BODY") ?: return



            // =====================================================
            // TRACK SMS (protocollo T#)
            // =====================================================
            if (msg.startsWith("T#")) {

                try {

                    val assembler = RxTrackAssembler()

                    val points = rxAssembler.processSms(msg)

                    if (points != null) {

                        for (p in points) {

                            val latLng = LatLng(p.first, p.second)

                            trackPoints.add(latLng)
                        }

                        Log.d("RX_TRACK", "SMS track ricevuto punti=${points.size}")

                        if (mapReady) drawAllPoints()
                    }

                } catch (e: Exception) {
                    Log.e("RX_TRACK", "Errore decoding SMS track", e)
                }

                return
            }

            Log.d("RX_DEBUG", "SMS RAW: [$msg]")

            // ⭐ GESTIONE MANUALE PRIMA DEL WHEN
            if (msg.contains("POS MANUALE")) {

                try {

                    val coordsRegex = Regex("""(-?\d+\.\d+),(-?\d+\.\d+)""")
                    val match = coordsRegex.find(msg)

                    if (match != null) {

                        val lat = match.groupValues[1].toDouble()
                        val lon = match.groupValues[2].toDouble()

                        val point = LatLng(lat, lon)

                        trackPoints.add(point)
                        manualPoints.add(point)

                        Log.d("BUFFER_TEST", "points loaded = ${trackPoints.size}")
                        Log.d("RX_DEBUG", "Manual point salvato: $lat,$lon")

                        if (mapReady) drawAllPoints()
                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                }

                return
            }

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

                    if (trackPoints.isNotEmpty()) {
                        generateFinalSnapshot()
                    }
                }

                "GPS_MANUAL" -> {

                    val lat = intent.getDoubleExtra("lat", 0.0)
                    val lon = intent.getDoubleExtra("lon", 0.0)

                    val point = LatLng(lat, lon)

                    trackPoints.add(point)
                    manualPoints.add(point)

                    if (mapReady) drawAllPoints()
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

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
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

        // ================================
        // BLOCCO CONFERMA BACK (RX MODE)
        // ================================

        onBackPressedDispatcher.addCallback(this) {

            AlertDialog.Builder(this@RxActivity)
                .setTitle("Chiudere modalità RX?")
                .setMessage("Il tracking è attivo. Vuoi davvero uscire?")
                .setPositiveButton("SI") { _, _ ->
                    finish()
                }
                .setNegativeButton("NO", null)
                .show()
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

        // =====================================
        // IMPORTA PUNTI DAL REPOSITORY SMS
        // =====================================
        if (TrackRepository.points.isNotEmpty()) {

            trackPoints.clear()

            for (p in TrackRepository.points) {

                val latLng = LatLng(p.first, p.second)

                trackPoints.add(latLng)
            }
        }

        // =====================================
        // DISEGNA TRACCIA
        // =====================================
        if (trackPoints.isNotEmpty()) {
            drawAllPoints()
        }

        // =====================================
        // OVERLAY MAPTILER
        // =====================================
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

        // 🔥 IMPORTANTISSIMO
        googleMap.mapType = GoogleMap.MAP_TYPE_NONE

        val apiKey = BuildConfig.MAPTILER_API_KEY

        if (apiKey.isBlank()) {
            Log.e("MAP_DEBUG", "MapTiler API KEY vuota!")
            return
        }

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
        googleMap.mapType = GoogleMap.MAP_TYPE_NORMAL
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

    private fun takeSnapshotSafely() {

        Log.d("SNAPSHOT_DEBUG", "takeSnapshotSafely start")

        Handler(Looper.getMainLooper()).postDelayed({

            takeSnapshot()

        }, 1500)
    }

    private fun generateFinalSnapshot() {


        if (!mapReady || trackPoints.isEmpty()) return

        val builder = LatLngBounds.Builder()
        trackPoints.forEach { builder.include(it) }
        val bounds = builder.build()

        googleMap.animateCamera(
            CameraUpdateFactory.newLatLngBounds(bounds, 150),
            1200,
            object : GoogleMap.CancelableCallback {

                override fun onFinish() {

                    Handler(Looper.getMainLooper()).postDelayed({
                        takeSnapshotSafely()
                    }, 1200)

                }

                override fun onCancel() {
                    takeSnapshotSafely()
                }
            }
        )
    }

    private fun takeSnapshot() {

        googleMap.snapshot { originalBitmap ->

            if (originalBitmap == null) return@snapshot
            if (trackPoints.isEmpty()) return@snapshot

            val bitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(bitmap)

            val paintText = Paint().apply {
                color = Color.BLACK
                textSize = 22f   // molto piccolo ma leggibile
                isAntiAlias = true
            }

            val paintCircle = Paint().apply {
                style = Paint.Style.FILL
                isAntiAlias = true
            }

            val projection = googleMap.projection

            trackPoints.forEachIndexed { index, latLng ->



                val point = projection.toScreenLocation(latLng)

                when {

                    manualPoints.any { manual ->
                        kotlin.math.abs(manual.latitude - latLng.latitude) < 0.00001 &&
                                kotlin.math.abs(manual.longitude - latLng.longitude) < 0.00001
                    } -> {

                        paintCircle.color = Color.YELLOW
                        canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), 18f, paintCircle)

                        paintText.textSize = 34f
                        paintText.color = Color.YELLOW
                        paintText.setShadowLayer(4f, 1f, 1f, Color.BLACK)

                        canvas.drawText(
                            "🌟",
                            point.x.toFloat() - 18f,
                            point.y.toFloat() + 12f,
                            paintText
                        )

                        // 🔹 coordinate piccole sopra la stella
                        paintText.textSize = 18f
                        paintText.color = Color.BLACK
                        paintText.clearShadowLayer()

                        canvas.drawText(
                            "${"%.6f".format(latLng.latitude)}, ${"%.6f".format(latLng.longitude)}",
                            point.x.toFloat() - 60f,
                            point.y.toFloat() - 25f,
                            paintText
                        )
                    }

                    index == 0 -> {

                        paintCircle.color = Color.GREEN
                        canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), 22f, paintCircle)

                        paintText.textSize = 30f
                        paintText.color = Color.WHITE
                        paintText.setShadowLayer(4f, 1f, 1f, Color.BLACK)

                        canvas.drawText(
                            "S",
                            point.x.toFloat() - 10f,
                            point.y.toFloat() + 10f,
                            paintText
                        )

                        paintText.clearShadowLayer()
                    }

                    index == trackPoints.lastIndex -> {

                        paintCircle.color = Color.RED
                        canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), 22f, paintCircle)

                        paintText.textSize = 30f
                        paintText.color = Color.WHITE
                        paintText.setShadowLayer(4f, 1f, 1f, Color.BLACK)

                        canvas.drawText(
                            "E",
                            point.x.toFloat() - 10f,
                            point.y.toFloat() + 10f,
                            paintText
                        )

                        paintText.clearShadowLayer()
                    }

                    else -> {

                        paintCircle.color = Color.BLACK
                        canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), 12f, paintCircle)

                        // Mostra etichetta solo ogni 5 punti
                        if (index % 5 == 0) {

                            canvas.drawText(
                                "P$index",
                                point.x.toFloat() + 10f,
                                point.y.toFloat(),
                                paintText
                            )
                        }
                    }
                }
            }

            drawInfoOverlay(canvas, bitmap)

            saveFinalBitmap(bitmap)
        }
    }

    private fun drawInfoOverlay(canvas: Canvas, bitmap: Bitmap) {

        val last = trackPoints.last()

        val geocoder = Geocoder(this, Locale.getDefault())

        var city = ""
        var province = ""

        try {
            val addresses = geocoder.getFromLocation(last.latitude, last.longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                city = addresses[0].locality ?: ""
                province = addresses[0].adminArea ?: ""
            }
        } catch (e: Exception) { }

        val date = SimpleDateFormat(
            "dd/MM/yyyy HH:mm:ss",
            Locale.getDefault()
        ).format(Date())

        val text = """
$city ${if (province.isNotEmpty()) "($province)" else ""}
$date
Ultima posizione:
${"%.6f".format(last.latitude)}, ${"%.6f".format(last.longitude)}
""".trimIndent()

        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 24f
            isAntiAlias = true
            setShadowLayer(3f, 1f, 1f, Color.WHITE)
        }

        var y = bitmap.height.toFloat() - 120f

        text.split("\n").forEach {
            canvas.drawText(it, 30f, y, paint)
            y += 28f
        }
    }
    private fun saveFinalBitmap(bitmap: Bitmap) {

        try {

            Log.d("SNAPSHOT_DEBUG", "saving bitmap")

            if (trackPoints.isEmpty()) return

            val last = trackPoints.last()

            // ================================
            // 📍 COMUNE (GEOCODER)
            // ================================
            var city = "UNKNOWN"

            try {
                val geocoder = Geocoder(this, Locale.ITALIAN)
                val addresses = geocoder.getFromLocation(last.latitude, last.longitude, 1)

                if (!addresses.isNullOrEmpty()) {
                    city = addresses[0].locality ?: "UNKNOWN"
                }

            } catch (e: Exception) {
                Log.e("SNAPSHOT_DEBUG", "Geocoder error", e)
            }

            // pulizia nome comune
            city = city
                .uppercase(Locale.ITALIAN)
                .replace(" ", "")
                .replace("[^A-Z]".toRegex(), "")

            if (city == "UNKNOWN") {
                city = "${"%.4f".format(last.latitude)}_${"%.4f".format(last.longitude)}"
            }

            // ================================
            // 📅 DATA FORMATO ITALIANO
            // ================================
            val now = Date()

            val day = SimpleDateFormat("dd", Locale.ITALIAN).format(now)
            val month = SimpleDateFormat("MMMM", Locale.ITALIAN)
                .format(now)
                .uppercase(Locale.ITALIAN)

            val year = SimpleDateFormat("yyyy", Locale.ITALIAN).format(now)

            val fileName = "${day}${month}${year}${city}.jpg"

            Log.d("SNAPSHOT_DEBUG", "filename=$fileName")

            // ================================
            // 🧠 RIDUZIONE MEMORIA
            // ================================
            val scaled = Bitmap.createScaledBitmap(
                bitmap,
                bitmap.width / 2,
                bitmap.height / 2,
                true
            )

            // ================================
            // 📂 SALVATAGGIO MEDIASTORE (ANDROID 10+)
            // ================================
            val resolver = contentResolver

            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SMSTracker")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }

            val uri = resolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )

            if (uri == null) {
                Log.e("SNAPSHOT_DEBUG", "uri null")
                return
            }

            resolver.openOutputStream(uri)?.use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }

            // 🔓 rendi visibile
            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)

            Log.d("SNAPSHOT_DEBUG", "SALVATO in GALLERIA: $fileName")

        } catch (e: Exception) {

            Log.e("SNAPSHOT_DEBUG", "save error", e)

        }
    }
}


