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
import com.example.smsgpstracker.rxmulti.RxMultiTrackRepository
import com.example.smsgpstracker.rxmulti.RxMultiExtraRepository
import com.example.smsgpstracker.rxmulti.RxPersistence
import android.provider.MediaStore
import android.net.Uri
import java.io.OutputStream
import android.content.ContentValues



class RxMultiActivity : AppCompatActivity(), OnMapReadyCallback {

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
    private val multiParser = RxMultiSmsParser()
    private val multiAssembler = RxMultiTrackAssembler()
    private val emergencyPoints = mutableListOf<LatLng>()
    private var emergencyBlink = false
    private val manualMarkers = mutableListOf<Marker>()
    private var firstCameraMove = true




    // =====================================================
    // RECEIVER SMS
    // =====================================================
    private val smsReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context?, intent: Intent?) {

            val type = intent?.getStringExtra("SMS_BODY") ?: return
            val raw = intent.getStringExtra("RAW_SMS")

            Log.e("STEP1_RAW_SMS", "RAW: [$raw]")
            Log.e("STEP1_BODY", "BODY: [$type]")
            Log.d("RX_DEBUG", "TYPE: [$type] RAW: [$raw]")

            // =====================================================
            // 🚨 EMERGENCY (USA RAW!)
            // =====================================================
            if (type == "EMERGENCY" && raw != null) {

                Log.d("DEBUG_EMERGENCY", "RAW: $raw")

                try {
                    val parts = raw.split("\\|")

                    if (parts.size >= 3) {

                        val coords = parts[2].split(",")

                        val lat = coords[0].toDouble()
                        val lon = coords[1].toDouble()

                        val point = LatLng(lat, lon)

                        // ✔ salva
                        emergencyPoints.add(point)
                        RxMultiExtraRepository.emergency.add(point)

                        Log.d("DEBUG_EMERGENCY", "AGGIUNTO: $lat,$lon")
                        Log.d("DEBUG_EMERGENCY", "SIZE: ${emergencyPoints.size}")

                        // 🔴 MARKER REALTIME
                        if (mapReady) {
                            updateEmergencyMarkers()

                            googleMap.animateCamera(
                                CameraUpdateFactory.newLatLngZoom(point, 17f)
                            )
                        }
                    }

                } catch (e: Exception) {
                    Log.e("DEBUG_EMERGENCY", "ERRORE", e)
                }

                return
            }


            // =====================================================
            // ⭐ POSIZIONE MANUALE (USA RAW!)
            // =====================================================
            if (type == "GPS_MANUAL" && raw != null) {

                Log.d("DEBUG_MANUAL", "RAW: $raw")

                try {

                    val coordsRegex = Regex("""(-?\d+\.\d+),\s*(-?\d+\.\d+)""")
                    val match = coordsRegex.find(raw)

                    if (match != null) {

                        val lat = match.groupValues[1].toDouble()
                        val lon = match.groupValues[2].toDouble()

                        val point = LatLng(lat, lon)

                        // ✔ salva
                        manualPoints.add(point)
                        RxMultiExtraRepository.manual.add(point)


                        Log.d("DEBUG_MANUAL", "AGGIUNTO: $lat,$lon")
                        Log.d("DEBUG_MANUAL", "SIZE: ${manualPoints.size}")

                        // 🟡 MARKER REALTIME
                        if (mapReady) drawAllPoints()

                    } else {
                        Log.e("DEBUG_MANUAL", "REGEX NON MATCHA")
                    }

                } catch (e: Exception) {
                    Log.e("DEBUG_MANUAL", "ERRORE", e)
                }

                return
            }

            // =====================================================
            // 📡 MULTI GPS PROTOCOL (TX|...)
            // =====================================================
            if (type.startsWith("TX|")) {

                Log.d("RX_MULTI", "SMS MULTI: $type")

                try {

                    val sms = type

                    val packet = multiParser.parse(sms) ?: run {
                        Log.e("RX_FLOW", "PARSE FALLITO per sms=$sms")
                        return
                    }

                    Log.e("RX_FLOW", "PARSE OK seq=${packet.seq}/${packet.total} type=${packet.type}")

                    // =========================
                    // 🆕 GESTIONE SESSIONE
                    // =========================
                    val previousSession = RxMultiTrackRepository.currentSessionId

                    if (previousSession != packet.sessionId) {

                        Log.e("RX_SESSION", "NUOVA SESSIONE → RESET UI")

                        trackPoints.clear()
                        RxMultiTrackRepository.points.clear()

                        multiAssembler.reset()

                        firstCameraMove = true
                    }

                    RxMultiTrackRepository.currentSessionId = packet.sessionId

                    // =========================
                    // 🔥 PROCESSA PACKET
                    // =========================
                    multiAssembler.process(packet)

                    // =========================
                    // 🔁 TRACK CUMULATIVO REALE
                    // =========================
                    val cumulative = multiAssembler.getFullTrack()

                    if (cumulative.isNotEmpty()) {

                        // 🔥 SALVATAGGIO PERSISTENTE (FONDAMENTALE)
                        RxMultiTrackRepository.points.clear()
                        RxMultiTrackRepository.points.addAll(cumulative)

                        // 🔥 UI
                        trackPoints.clear()
                        trackPoints.addAll(cumulative.map { LatLng(it.first, it.second) })

                        if (mapReady) drawAllPoints()
                    }

                    // =========================
                    // 🏁 CHIUSURA SU F (ROBUSTA)
                    // =========================
                    if (packet.type == "F") {

                        Log.e("RX_FINAL", "F RICEVUTO → chiusura tracking")

                        val finalTrack = multiAssembler.buildFinalTrack()

                        if (finalTrack != null && finalTrack.isNotEmpty()) {

                            RxMultiTrackRepository.points.clear()
                            RxMultiTrackRepository.points.addAll(finalTrack)

                            trackPoints.clear()
                            trackPoints.addAll(finalTrack.map { LatLng(it.first, it.second) })

                            if (mapReady) drawAllPoints()
                        }

                        txtStatus.text = "Tracking completato"

                        if (trackPoints.isNotEmpty()) {
                            Handler(mainLooper).postDelayed({
                                generateFinalSnapshot()
                            }, 2000)
                        }

                        // 🔴 RESET DOPO SNAPSHOT (NON SUBITO!)
                        Handler(mainLooper).postDelayed({
                            multiAssembler.reset()
                        }, 4000)

                        return
                    }

                } catch (e: Exception) {
                    Log.e("RX_MULTI", "Errore MULTI", e)
                }

                return
            }

            // =====================================================
            // DEBUG FALLBACK
            // =====================================================
            Log.d("RX_DEBUG", "SMS NON GESTITO: [$type]")
        }
    }

    private fun startEmergencyBlink() {

        val handler = Handler(mainLooper)

        val runnable = object : Runnable {
            override fun run() {

                emergencyBlink = !emergencyBlink

                // ❌ NON ridisegnare tutta la mappa!
                // aggiorna solo marker emergency

                updateEmergencyMarkers()

                handler.postDelayed(this, 1000) // più lento = meno stress
            }
        }

        handler.post(runnable)
    }

    private var emergencyMarkers = mutableListOf<Marker>()

    private fun updateEmergencyMarkers() {

        // rimuovi vecchi
        emergencyMarkers.forEach { it.remove() }
        emergencyMarkers.clear()

        if (!emergencyBlink) return

        emergencyPoints.forEach { point ->

            val marker = googleMap.addMarker(
                MarkerOptions()
                    .position(point)
                    .title("🚨 EMERGENCY")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            )

            if (marker != null) emergencyMarkers.add(marker)
        }
    }

    // =====================================================
    // ON CREATE
    // =====================================================
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rx_multi)

        // ================================
        // 🔥 RESET SOLO AL PRIMO AVVIO
        // ================================
        if (savedInstanceState == null) {

            Log.e("RX_INIT", "FIRST START → RESET COMPLETO")

            trackPoints.clear()
            RxMultiTrackRepository.points.clear()
            RxMultiTrackRepository.currentSessionId = null
            multiAssembler.reset()

        } else {

            Log.e("RX_INIT", "RECREATE (ROTATION) → MANTENGO DATI")

            // 🔁 RIPRISTINO DA REPOSITORY (già in memoria)
            trackPoints.clear()

            RxMultiTrackRepository.points.forEach {
                trackPoints.add(LatLng(it.first, it.second))
            }
        }

        // ================================
        // 🔥 RIPRISTINO TRACK (DISABILITATO)
        // ================================
        val (savedSession, savedPoints) = RxPersistence.loadTrack(this)

        val restoreEnabled = false // puoi riattivarlo dopo stabilizzazione

        if (restoreEnabled && savedPoints.isNotEmpty()) {

            trackPoints.addAll(savedPoints.map { LatLng(it.first, it.second) })

            RxMultiTrackRepository.points.addAll(savedPoints)
            RxMultiTrackRepository.currentSessionId = savedSession

            Log.d("RX_RESTORE", "Track ripristinato: ${savedPoints.size} punti")
        }

        // ================================
        // 🚨 EMERGENCY BLINK
        // ================================
        startEmergencyBlink()

        // ================================
        // ⚙️ SETTINGS MAPPA
        // ================================
        prefs = getSharedPreferences("map_settings", MODE_PRIVATE)
        selectedMapProvider = prefs.getString("provider", "GOOGLE") ?: "GOOGLE"

        // ================================
        // UI
        // ================================
        txtStatus = findViewById(R.id.txtStatus)
        txtCount = findViewById(R.id.txtCount)
        txtLast = findViewById(R.id.txtLast)

        txtStatus.text = "RX ATTIVO"

        // ================================
        // MAP
        // ================================
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // ================================
        // 📡 REGISTER RECEIVER
        // ================================
        val filter = IntentFilter(SmsCommandProcessor.ACTION_SMS_EVENT)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(smsReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(smsReceiver, filter)
        }

        receiverRegistered = true

        // ================================
        // 🗺️ SWITCH MAP PROVIDER
        // ================================
        val switchMapType = findViewById<Switch>(R.id.switchMapType)

        switchMapType.isChecked = selectedMapProvider == "MAPTILER"

        switchMapType.setOnCheckedChangeListener { _, isChecked ->

            selectedMapProvider = if (isChecked) "MAPTILER" else "GOOGLE"
            prefs.edit().putString("provider", selectedMapProvider).apply()

            if (isChecked) enableMapTilerOverlay()
            else disableMapTilerOverlay()
        }

        // ================================
        // 🔙 BACK CONFIRM
        // ================================
        onBackPressedDispatcher.addCallback(this) {

            AlertDialog.Builder(this@RxMultiActivity)
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
        // 🔥 1. RIPRISTINO DATI (ROTATION SAFE)
        // =====================================

        // TRACK
        trackPoints.clear()
        RxMultiTrackRepository.points.forEach {
            trackPoints.add(LatLng(it.first, it.second))
        }

        // MANUAL
        manualPoints.clear()
        manualPoints.addAll(RxMultiExtraRepository.manual)

        // EMERGENCY

        emergencyPoints.clear()
        updateEmergencyMarkers()
        emergencyPoints.addAll(RxMultiExtraRepository.emergency)

        if (selectedMapProvider == "MAPTILER") {

            enableMapTilerOverlay()

            Handler(mainLooper).postDelayed({
                if (trackPoints.isNotEmpty()) {
                    drawAllPoints()
                }
            }, 800)

        } else {
            if (trackPoints.isNotEmpty()) {
                drawAllPoints()
            }
        }
    }

    // =====================================================
    // DRAW TRACK
    // =====================================================
    private fun drawAllPoints() {

        if (!mapReady || trackPoints.isEmpty()) return

        // ❌ NON usare più clear()
        // googleMap.clear()

        // =========================
        // 📍 POLYLINE (aggiorna invece di ricreare)
        // =========================
        if (trackPolyline == null) {
            trackPolyline = googleMap.addPolyline(
                PolylineOptions()
                    .addAll(trackPoints)
                    .width(4f)
                    .color(Color.BLACK)
            )
        } else {
            if (trackPolyline?.points?.size != trackPoints.size) {
                trackPolyline?.points = trackPoints
            }
        }

        // =========================
        // 🔴 ULTIMO PUNTO
        // =========================
        val last = trackPoints.last()

        if (lastMarker == null) {
            lastMarker = googleMap.addMarker(
                MarkerOptions()
                    .position(last)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            )
        } else {
            lastMarker?.position = last
        }

        // =========================
        // ⭐ MANUAL POINTS (NO DUPLICATI)
        // =========================
        // pulisci vecchi
        manualMarkers.forEach { it.remove() }
        manualMarkers.clear()

        manualPoints.forEach { point ->

            val marker = googleMap.addMarker(
                MarkerOptions()
                    .position(point)
                    .title("⭐ Posizione manuale")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW))
            )

            if (marker != null) manualMarkers.add(marker)
        }



        // =========================
        // 📦 CAMERA (solo all'inizio!)
        // =========================
        if (firstCameraMove) {

            val builder = LatLngBounds.Builder()

            trackPoints.forEach { builder.include(it) }
            manualPoints.forEach { builder.include(it) }
            emergencyPoints.forEach { builder.include(it) }

            googleMap.animateCamera(
                CameraUpdateFactory.newLatLngBounds(builder.build(), 150)
            )

            firstCameraMove = false
        }

        // =========================
        // 📊 UI
        // =========================
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
        RxMultiExtraRepository.manual.clear()
        RxMultiExtraRepository.emergency.clear()
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

    private fun takeSnapshotWithRetry(attempt: Int) {

        googleMap.snapshot { bitmap ->

            if (bitmap == null) {

                if (attempt < 3) {

                    Handler(Looper.getMainLooper()).postDelayed({

                        takeSnapshotWithRetry(attempt + 1)

                    }, 1500)

                } else {

                    Log.e("SNAPSHOT", "Snapshot fallita")
                }

                return@snapshot
            }

            takeSnapshot()
        }
    }

    private fun generateFinalSnapshot() {

        if (!mapReady || trackPoints.isEmpty()) return

        val builder = LatLngBounds.Builder()

        trackPoints.forEach { builder.include(it) }
        manualPoints.forEach { builder.include(it) }
        emergencyPoints.forEach { builder.include(it) }

        val bounds = builder.build()

        googleMap.animateCamera(
            CameraUpdateFactory.newLatLngBounds(bounds, 150),
            3000,
            object : GoogleMap.CancelableCallback {

                override fun onFinish() {

                    googleMap.setOnMapLoadedCallback {

                        Log.d("SNAPSHOT", "MAP LOADED")

                        // 🔥 WAIT DINAMICO
                        val extraDelay =
                            if (selectedMapProvider == "MAPTILER")
                                3500L
                            else
                                800L

                        Handler(Looper.getMainLooper()).postDelayed({

                            takeSnapshotWithRetry(0)

                        }, extraDelay)
                    }
                }

                override fun onCancel() {
                    takeSnapshotSafely()
                }
            }
        )
    }



    private fun takeSnapshot() {

        Log.d("DEBUG_SNAPSHOT", "trackPoints: ${trackPoints.size}")
        Log.d("DEBUG_SNAPSHOT", "manualPoints: ${manualPoints.size}")
        Log.d("DEBUG_SNAPSHOT", "emergencyPoints: ${emergencyPoints.size}")

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

                    // 🟢 START
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

                    // 🔴 END
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

                    // ⚫ PUNTI NORMALI
                    else -> {

                        paintCircle.color = Color.BLACK
                        canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), 9f, paintCircle)

                        // etichetta ogni 5 punti
                        if (index % 5 == 0) {

                            paintText.textSize = 22f
                            paintText.color = Color.BLACK

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
            manualPoints.forEach { manual ->

                Log.d("DEBUG_DRAW", "Disegno MANUALE: ${manual.latitude},${manual.longitude}")
                val isOverEmergency = emergencyPoints.any {
                    kotlin.math.abs(it.latitude - manual.latitude) < 0.0001 &&
                            kotlin.math.abs(it.longitude - manual.longitude) < 0.0001
                }

                if (isOverEmergency) return@forEach

                val p = projection.toScreenLocation(manual)

                paintCircle.color = Color.YELLOW
                canvas.drawCircle(p.x.toFloat(), p.y.toFloat(), 20f, paintCircle)

                paintText.textSize = 34f
                paintText.color = Color.YELLOW
                paintText.setShadowLayer(4f, 1f, 1f, Color.BLACK)

                canvas.drawText(
                    "🌟",
                    p.x.toFloat() - 18f,
                    p.y.toFloat() + 12f,
                    paintText
                )
                paintText.textSize = 18f
                paintText.color = Color.BLACK
                paintText.clearShadowLayer()

                canvas.drawText(
                    "${"%.6f".format(manual.latitude)}, ${"%.6f".format(manual.longitude)}",
                    p.x.toFloat() - 80f,
                    p.y.toFloat() - 25f,
                    paintText
                )

                paintText.clearShadowLayer()
            }

            emergencyPoints.forEach { em ->

                Log.d("DEBUG_DRAW", "Disegno EMERGENCY: ${em.latitude},${em.longitude}")
                val p = projection.toScreenLocation(em)

                paintCircle.color = Color.RED
                canvas.drawCircle(p.x.toFloat(), p.y.toFloat(), 26f, paintCircle)

                paintText.textSize = 18f
                paintText.color = Color.RED
                paintText.clearShadowLayer()

                canvas.drawText(
                    "${"%.6f".format(em.latitude)}, ${"%.6f".format(em.longitude)}",
                    p.x.toFloat() - 80f,
                    p.y.toFloat() - 30f,
                    paintText
                )

                paintText.clearShadowLayer()
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
    private fun generateFileName(lat: Double?, lon: Double?): String {

        val date = java.text.SimpleDateFormat("ddMMMM yyyy", java.util.Locale.ITALIAN)
            .format(java.util.Date())
            .uppercase()

        val location = try {
            if (lat != null && lon != null) {
                val geocoder = android.location.Geocoder(this, java.util.Locale.ITALIAN)
                val list = geocoder.getFromLocation(lat, lon, 1)

                if (!list.isNullOrEmpty()) {
                    list[0].locality ?: "UNKNOWN"
                } else "UNKNOWN"
            } else "UNKNOWN"
        } catch (e: Exception) {
            "UNKNOWN"
        }

        return "${date.replace(" ", "")}${location.uppercase()}.jpg"
    }
    private fun saveFinalBitmap(bitmap: Bitmap) {

        try {

            val scaled = Bitmap.createScaledBitmap(
                bitmap,
                bitmap.width / 2,
                bitmap.height / 2,
                true
            )

            // 🔥 PRENDI ULTIMA POSIZIONE
            val lastPoint = trackPoints.lastOrNull()
            val lat = lastPoint?.latitude
            val lon = lastPoint?.longitude

            val filename = generateFileName(lat, lon)

            val outputStream: OutputStream?

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

                val resolver = contentResolver

                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/SMSTracker")
                }

                val imageUri = resolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                )

                outputStream = imageUri?.let { resolver.openOutputStream(it) }

            } else {

                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "SMSTracker"
                )

                if (!dir.exists()) dir.mkdirs()

                val file = File(dir, filename)
                outputStream = FileOutputStream(file)

                sendBroadcast(
                    Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file))
                )
            }

            outputStream?.use {
                scaled.compress(Bitmap.CompressFormat.JPEG, 90, it)
            }

            Log.d("SNAPSHOT", "SALVATO OK: $filename")

        } catch (e: Exception) {
            Log.e("SNAPSHOT", "ERRORE SALVATAGGIO", e)
        }
    }
}
