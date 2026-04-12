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
    private val manualMarkerMap = mutableMapOf<String, Marker>()
    private var firstCameraMove = true
    private var snapshotDone = false


    private fun handleTrackSms(sms: String) {


        try {
            val packet = multiParser.parse(sms)

            if (packet == null) {
                Log.e("RX_MULTI_DEBUG", "PARSE FALLITO per SMS: $sms")
                return
            }

            // 🔥 RESET SNAPSHOT SU NUOVA SESSIONE / PRIMO PACCHETTO
            if (multiAssembler.isNewSession(packet)) {
                snapshotDone = false
                Log.d("RX_MULTI_DEBUG", "NUOVA SESSIONE REALE")
            }

            Log.d("RX_MULTI_DEBUG", "PROCESSATO seq=${packet.seq} type=${packet.type}")

            // 🔥 UPDATE SESSIONE
            val fullTrack = multiAssembler.process(packet)

            if (packet.type == "F" && fullTrack.isNotEmpty()) {

                trackPoints.clear()
                trackPoints.addAll(fullTrack.map { LatLng(it.first, it.second) })

                RxMultiTrackRepository.points.clear()
                RxMultiTrackRepository.points.addAll(fullTrack)

                snapshotDone = true

                Log.d("RX_MULTI", "TRACK COMPLETO DA ASSEMBLER")

                Handler(mainLooper).postDelayed({
                    generateFinalSnapshot()
                }, 1500)

                txtStatus.text = "Tracking completato"

                return
            }

            // 🔥 TRACK PARZIALE
            val partialRaw = multiAssembler.getPartialTrack(packet.sessionId)
            Log.d("RX_MULTI_DEBUG", "TRACK SIZE DOPO UPDATE: ${partialRaw.size}")

            if (partialRaw.isEmpty()) return

            // 🔥 STEP 2.3 → DEDUPLICAZIONE
            val deduped = dedupeLatLng(
                partialRaw.map { LatLng(it.first, it.second) }
            )

            trackPoints.clear()
            trackPoints.addAll(deduped)

            // 🔥 SYNC REPOSITORY
            RxMultiTrackRepository.points.clear()
            RxMultiTrackRepository.points.addAll(partialRaw)

            val typeClean = packet.type.trim()

            Log.d("RX_MULTI_DEBUG", "CHECK TYPE: [$typeClean]")

            // =========================
            // 🎯 UI UPDATE
            // =========================
            if (mapReady) drawAllPoints()

            txtStatus.text =
                if (typeClean == "F") "Tracking completato"
                else "RX ATTIVO"

            // =========================
            // 🔥 CHIUSURA NORMALE (F)
            // =========================
            if (typeClean == "F" && !snapshotDone) {

                val isComplete = multiAssembler.isSessionComplete(packet.sessionId)

                if (isComplete) {

                    snapshotDone = true

                    Log.d("RX_MULTI", "F → SNAPSHOT COMPLETO")

                    Handler(mainLooper).postDelayed({
                        generateFinalSnapshot()
                    }, 1500)

                } else {

                    Log.w("RX_MULTI", "F ricevuto ma sessione incompleta")

                    // opzionale: timeout recovery
                }
            }



            // =========================
            // 🔍 DEBUG SESSIONE
            // =========================
            Log.d(
                "RX_SESSION",
                "Session ${packet.sessionId} seq=${packet.seq} type=$typeClean size=${trackPoints.size}"
            )

        } catch (e: Exception) {
            Log.e("RX_MULTI", "Errore handleTrackSms", e)
        }
    }




    // =====================================================
    // RECEIVER SMS
    // =====================================================
    private val smsReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context?, intent: Intent?) {

            val typeRaw = intent?.getStringExtra("SMS_BODY") ?: return
            val raw = intent.getStringExtra("RAW_SMS")

            val type = typeRaw.trim().uppercase()

            Log.d("RX_DEBUG", "TYPE RAW: [$typeRaw] → NORMALIZED: [$type] RAW: [$raw]")

            // =====================================================
            // 🚨 EMERGENCY
            // =====================================================
            if (raw != null && raw.contains("CTRL|EMERGENCY")) {

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

                            // ❌ NON zoommare sempre!
                            if (trackPoints.isEmpty()) {
                                googleMap.animateCamera(
                                    CameraUpdateFactory.newLatLngZoom(point, 17f)
                                )
                            }
                        }
                    }

                } catch (e: Exception) {
                    Log.e("DEBUG_EMERGENCY", "ERRORE", e)
                }

                return
            }


            // =====================================================
            // ⭐ GPS MANUAL
            // =====================================================
            if (type.startsWith("GPS_MANUAL") && raw != null) {

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
                        if (mapReady) {
                            // NON ridisegnare tutto → solo marker
                            drawManualMarkersOnly()
                        }

                    } else {
                        Log.e("DEBUG_MANUAL", "REGEX NON MATCHA")
                    }

                } catch (e: Exception) {
                    Log.e("DEBUG_MANUAL", "ERRORE", e)
                }

                return
            }

            // =====================================================
            // 📡 MULTI GPS PROTOCOL
            // =====================================================
            if (typeRaw.startsWith("TX|")) {
                handleTrackSms(typeRaw)
                return
            }

            // =====================================================
            // DEBUG FALLBACK
            // =====================================================
            Log.d("RX_DEBUG", "SMS NON GESTITO: [$type]")
        }
    }

    private fun drawManualMarkersOnly() {

        manualPoints.forEach { point ->

            val key = "${point.latitude}_${point.longitude}"

            if (!manualMarkerMap.containsKey(key)) {

                val marker = googleMap.addMarker(
                    MarkerOptions()
                        .position(point)
                        .title("⭐ Posizione manuale")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW))
                )

                if (marker != null) {
                    manualMarkerMap[key] = marker
                }
            }
        }
    }

    private fun distance(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        val dLat = a.first - b.first
        val dLon = a.second - b.second
        return kotlin.math.sqrt(dLat * dLat + dLon * dLon)
    }

    private fun deduplicate(points: List<Pair<Double, Double>>): List<Pair<Double, Double>> {

        val result = mutableListOf<Pair<Double, Double>>()
        var last: Pair<Double, Double>? = null

        for (p in points) {
            if (last == null || distance(p, last) > 0.00001) {
                result.add(p)
                last = p
            }
        }

        return result
    }

    private fun dedupeLatLng(points: List<LatLng>): List<LatLng> {
        val result = mutableListOf<LatLng>()

        points.forEach { p ->
            if (result.isEmpty() || !p.isCloseTo(result.last())) {
                result.add(p)
            }
        }

        return result
    }

    private fun LatLng.isCloseTo(other: LatLng, threshold: Double = 0.00001): Boolean {
        return kotlin.math.abs(latitude - other.latitude) < threshold &&
                kotlin.math.abs(longitude - other.longitude) < threshold
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

        // se non esistono → creali UNA VOLTA
        if (emergencyMarkers.size != emergencyPoints.size) {

            emergencyMarkers.forEach { it.remove() }
            emergencyMarkers.clear()

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

        // 🔥 blink SOLO visibility
        emergencyMarkers.forEach {
            it.isVisible = emergencyBlink
        }
    }


    private fun handleEmergency(point: LatLng) {

        // 1. Marker rosso
        googleMap.addMarker(
            MarkerOptions()
                .position(point)
                .title("SOS")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
        )

        // 2. Alert utente
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle("🚨 EMERGENCY")
                .setMessage("Posizione ricevuta:\n${point.latitude}, ${point.longitude}")
                .setPositiveButton("OK", null)
                .show()
        }

        // 3. Log
        Log.d("RX_MULTI", "EMERGENCY at ${point.latitude}, ${point.longitude}")
    }

    // =====================================================
    // ON CREATE
    // =====================================================
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rx_multi)
        startEmergencyBlink()

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
        emergencyPoints.addAll(RxMultiExtraRepository.emergency)
        updateEmergencyMarkers()

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
                    .width(6f)
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
        manualPoints.forEach { point ->

            val key = "${point.latitude}_${point.longitude}"

            if (!manualMarkerMap.containsKey(key)) {

                val marker = googleMap.addMarker(
                    MarkerOptions()
                        .position(point)
                        .title("⭐ Posizione manuale")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW))
                )

                if (marker != null) {
                    manualMarkerMap[key] = marker
                }
            }
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

        }, 500) // 🔥 basta mezzo secondo ora
    }

    private fun generateFinalSnapshot() {

        if (!mapReady || trackPoints.isEmpty()) return

        val builder = LatLngBounds.Builder()
        trackPoints.forEach { builder.include(it) }
        val bounds = builder.build()

        googleMap.animateCamera(
            CameraUpdateFactory.newLatLngBounds(bounds, 150),
            3000,
            object : GoogleMap.CancelableCallback {

                override fun onFinish() {

                    Log.d("SNAPSHOT_DEBUG", "Camera animation finished")

                    var snapshotDone = false

                    // 🔥 CALLBACK CORRETTO
                    googleMap.setOnMapLoadedCallback {

                        if (!snapshotDone) {
                            snapshotDone = true

                            Log.d("SNAPSHOT_DEBUG", "Map fully loaded → snapshot")

                            takeSnapshotSafely()
                        }
                    }

                    // 🔥 FALLBACK SICURO
                    Handler(Looper.getMainLooper()).postDelayed({

                        if (!snapshotDone) {
                            snapshotDone = true

                            Log.w("SNAPSHOT_DEBUG", "Fallback snapshot")

                            takeSnapshot()
                        }

                    }, 4000)
                }

                override fun onCancel() {

                    Log.d("SNAPSHOT_DEBUG", "Camera cancelled")

                    googleMap.setOnMapLoadedCallback {
                        takeSnapshotSafely()
                    }
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

            // =========================
            // 🔥 SCALE FACTOR (QUALITÀ)
            // =========================
            val scaleFactor = 2f

            val scaledBitmap = Bitmap.createScaledBitmap(
                originalBitmap,
                (originalBitmap.width * scaleFactor).toInt(),
                (originalBitmap.height * scaleFactor).toInt(),
                true
            )

            val bitmap = scaledBitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(bitmap)

            // ⚠️ scala il canvas (tutto viene disegnato proporzionato)
            canvas.scale(scaleFactor, scaleFactor)

            // =========================
            // 🎯 DIMENSIONI DINAMICHE
            // =========================
            val startEndRadius = 22f / scaleFactor
            val normalRadius = 6f / scaleFactor
            val manualRadius = 20f / scaleFactor
            val emergencyRadius = 26f / scaleFactor

            val textSmall = 18f / scaleFactor
            val textNormal = 22f / scaleFactor
            val textBig = 30f / scaleFactor

            // =========================
            // 🎨 PAINT
            // =========================
            val paintText = Paint().apply {
                color = Color.BLACK
                textSize = textNormal
                isAntiAlias = true
            }

            val paintCircle = Paint().apply {
                style = Paint.Style.FILL
                isAntiAlias = true
            }

            val projection = googleMap.projection

            // =========================
            // 🔴 TRACK POINTS
            // =========================
            trackPoints.forEachIndexed { index, latLng ->

                val point = projection.toScreenLocation(latLng)

                when {

                    // 🟢 START
                    index == 0 -> {

                        paintCircle.color = Color.GREEN
                        canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), startEndRadius, paintCircle)

                        paintText.textSize = textBig
                        paintText.color = Color.WHITE
                        paintText.setShadowLayer(4f / scaleFactor, 1f, 1f, Color.BLACK)

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
                        canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), startEndRadius, paintCircle)

                        paintText.textSize = textBig
                        paintText.color = Color.WHITE
                        paintText.setShadowLayer(4f / scaleFactor, 1f, 1f, Color.BLACK)

                        canvas.drawText(
                            "E",
                            point.x.toFloat() - 10f,
                            point.y.toFloat() + 10f,
                            paintText
                        )

                        paintText.clearShadowLayer()
                    }

                    // ⚫ NORMALI
                    else -> {

                        paintCircle.color = Color.BLACK
                        canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), normalRadius, paintCircle)

                        // etichetta ogni 5 punti
                        if (index % 5 == 0) {

                            paintText.textSize = textSmall
                            paintText.color = Color.BLACK

                            canvas.drawText(
                                "P$index",
                                point.x.toFloat() + 8f,
                                point.y.toFloat(),
                                paintText
                            )
                        }
                    }
                }
            }

            // =========================
            // 🟡 MANUAL POINTS
            // =========================
            manualPoints.forEach { manual ->

                val isOverEmergency = emergencyPoints.any {
                    kotlin.math.abs(it.latitude - manual.latitude) < 0.0001 &&
                            kotlin.math.abs(it.longitude - manual.longitude) < 0.0001
                }

                if (isOverEmergency) return@forEach

                val p = projection.toScreenLocation(manual)

                paintCircle.color = Color.YELLOW
                canvas.drawCircle(p.x.toFloat(), p.y.toFloat(), manualRadius, paintCircle)

                paintText.textSize = textBig
                paintText.color = Color.YELLOW
                paintText.setShadowLayer(4f / scaleFactor, 1f, 1f, Color.BLACK)

                canvas.drawText(
                    "★",
                    p.x.toFloat() - 15f,
                    p.y.toFloat() + 10f,
                    paintText
                )

                paintText.clearShadowLayer()

                paintText.textSize = textSmall
                paintText.color = Color.BLACK

                canvas.drawText(
                    "${"%.6f".format(manual.latitude)}, ${"%.6f".format(manual.longitude)}",
                    p.x.toFloat() - 70f,
                    p.y.toFloat() - 20f,
                    paintText
                )
            }

            // =========================
            // 🔴 EMERGENCY POINTS
            // =========================
            emergencyPoints.forEach { em ->

                val p = projection.toScreenLocation(em)

                paintCircle.color = Color.RED
                canvas.drawCircle(p.x.toFloat(), p.y.toFloat(), emergencyRadius, paintCircle)

                paintText.textSize = textSmall
                paintText.color = Color.RED

                canvas.drawText(
                    "${"%.6f".format(em.latitude)}, ${"%.6f".format(em.longitude)}",
                    p.x.toFloat() - 70f,
                    p.y.toFloat() - 25f,
                    paintText
                )
            }

            // =========================
            // 🧾 INFO OVERLAY
            // =========================
            drawInfoOverlay(canvas, bitmap)

            // =========================
            // 💾 SAVE
            // =========================
            saveFinalBitmap(bitmap)
        }
    }

    private fun isNearManualPoint(latLng: LatLng): Boolean {

        return manualPoints.any { manual ->

            val dLat = latLng.latitude - manual.latitude
            val dLon = latLng.longitude - manual.longitude

            val distance = Math.sqrt(dLat * dLat + dLon * dLon)

            distance < 0.0005   // ~50 metri
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

            // riduce uso memoria (fondamentale per Android 7)
            val scaled = Bitmap.createScaledBitmap(
                bitmap,
                bitmap.width / 2,
                bitmap.height / 2,
                true
            )

            val dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)

            if (dir == null) {
                Log.e("SNAPSHOT_DEBUG", "pictures dir null")
                return
            }

            val file = File(
                dir,
                "track_${System.currentTimeMillis()}.jpg"
            )

            val out = FileOutputStream(file)

            scaled.compress(Bitmap.CompressFormat.JPEG, 90, out)

            out.flush()
            out.close()

            Log.d("SNAPSHOT_DEBUG", "bitmap saved: ${file.absolutePath}")

        } catch (e: Exception) {

            Log.e("SNAPSHOT_DEBUG", "save error", e)

        }
    }
}

