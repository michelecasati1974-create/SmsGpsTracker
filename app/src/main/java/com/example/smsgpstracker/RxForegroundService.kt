package com.example.smsgpstracker

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.android.gms.maps.model.LatLng
import java.io.*
import android.app.AlarmManager
import android.app.PendingIntent
import android.os.SystemClock
import android.content.Context



class RxForegroundService : Service() {

    private val channelId = "RX_TRACK_CHANNEL"
    private var isForegroundStarted = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        when (intent?.action) {

            "START_TRACK" -> {

                if (!isForegroundStarted) {
                    startForegroundInternal()
                    isForegroundStarted = true
                }

                resetTrackFile()
            }

            "ADD_POINT" -> {

                val lat = intent.getDoubleExtra("lat", 0.0)
                val lon = intent.getDoubleExtra("lon", 0.0)

                savePoint(lat, lon)
            }

            "END_TRACK" -> {

                val points = readPoints()

                if (points.isNotEmpty()) {

                    // Prepara intent per RxExportForegroundService
                    val exportIntent = Intent(this@RxForegroundService, RxExportForegroundService::class.java)

                    // Converti punti in ArrayList<LatLng>
                    val latLngList = ArrayList<LatLng>()
                    points.forEach { latLngList.add(LatLng(it.latitude, it.longitude)) }

                    exportIntent.putParcelableArrayListExtra("TRACK_POINTS", latLngList)

                    // Avvia servizio foreground per generare immagine
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(exportIntent)
                    } else {
                        startService(exportIntent)
                    }
                }

                // Ripulisce il file track
                resetTrackFile()

                // Ferma servizio RxForegroundService
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)

        // Riavvia servizio se sistema lo chiude
        val restartServiceIntent =
            Intent(applicationContext, RxExportForegroundService::class.java)
        restartServiceIntent.setPackage(packageName)

        val restartServicePendingIntent = PendingIntent.getService(
            this,
            1,
            restartServiceIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmService = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmService.set(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + 1000,
            restartServicePendingIntent
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // =====================================================
    // FOREGROUND
    // =====================================================

    private fun startForegroundInternal() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Tracking RX",
                NotificationManager.IMPORTANCE_LOW
            )

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("RX Tracking attivo")
            .setContentText("Registrazione posizioni in corso...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()

        startForeground(1, notification)
    }

    // =====================================================
    // FILE TRACK
    // =====================================================

    private fun resetTrackFile() {
        val file = File(filesDir, "track.txt")
        file.writeText("")
    }

    private fun savePoint(lat: Double, lon: Double) {
        val file = File(filesDir, "track.txt")
        FileWriter(file, true).use {
            it.append("$lat,$lon\n")
        }
    }

    private fun readPoints(): List<LatLng> {

        val list = mutableListOf<LatLng>()
        val file = File(filesDir, "track.txt")

        if (!file.exists()) return list

        BufferedReader(FileReader(file)).use { reader ->

            var line: String?

            while (reader.readLine().also { line = it } != null) {

                val parts = line!!.split(",")

                if (parts.size == 2) {
                    list.add(
                        LatLng(
                            parts[0].toDouble(),
                            parts[1].toDouble()
                        )
                    )
                }
            }
        }

        return list
    }
}

