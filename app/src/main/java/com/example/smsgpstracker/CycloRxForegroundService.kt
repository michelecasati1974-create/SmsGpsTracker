package com.example.smsgpstracker.cyclosm

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.smsgpstracker.cyclosm.composer.MapComposer
import com.example.smsgpstracker.cyclosm.export.MapExporter
import com.example.smsgpstracker.cyclosm.overlay.MarkerOverlay
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class CycloRxForegroundService : Service() {

    private val CHANNEL_ID = "cyclo_export_channel"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CyclOSM Export")
            .setContentText("Generazione mappa in corso...")
            .setSmallIcon(android.R.drawable.ic_menu_mapmode)
            .setOngoing(true)
            .build()

        startForeground(1, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        // Esempio: coordinate bounding box (puoi calcolarle dinamicamente)
        val latMin = 45.0
        val lonMin = 9.0
        val latMax = 45.1
        val lonMax = 9.1
        val zoom = 16

        val trackPoints = listOf(
            LatLng(45.05, 9.05),
            LatLng(45.06, 9.06)
        )

        scope.launch {
            try {
                // Step 3: composizione bitmap
                val bitmap: Bitmap? = MapComposer.composeMap(latMin, lonMin, latMax, lonMax, zoom)

                bitmap?.let { bmp ->
                    // Step 4: overlay marker + info
                    trackPoints.forEach { point ->
                        MarkerOverlay.drawMarker(bmp, point, latMin, lonMin, zoom)
                    }

                    // aggiunta info testuale
                    addInfoOverlay(bmp, trackPoints.last())

                    // Step 5: export MediaStore
                    MapExporter.saveBitmap(applicationContext, bmp, "CyclOSM")

                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun addInfoOverlay(bitmap: Bitmap, lastPoint: LatLng) {
        val canvas = android.graphics.Canvas(bitmap)
        val textPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 50f
            isAntiAlias = true
        }
        val bgPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            alpha = 220
        }

        val date = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
            .format(Date())

        val infoText = "Tracking CyclOSM\n$date\nLat: ${lastPoint.latitude}\nLon: ${lastPoint.longitude}"
        val padding = 40f
        val lineHeight = 60f
        val lines = infoText.split("\n")
        val boxHeight = lines.size * lineHeight + padding

        canvas.drawRect(
            20f,
            bitmap.height - boxHeight - 20f,
            bitmap.width - 20f,
            bitmap.height - 20f,
            bgPaint
        )

        var y = bitmap.height - boxHeight + 60f
        lines.forEach {
            canvas.drawText(it, 40f, y, textPaint)
            y += lineHeight
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "CyclOSM Export Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
