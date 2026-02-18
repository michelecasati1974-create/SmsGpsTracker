package com.example.smsgpstracker

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import com.google.android.gms.maps.model.LatLng
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import android.content.ContentValues



class RxExportForegroundService : Service() {

    private val CHANNEL_ID = "export_channel"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SmsGpsTracker")
            .setContentText("Generazione immagine tracking in corso...")
            .setSmallIcon(android.R.drawable.ic_menu_mapmode)
            .setOngoing(true)
            .build()

        startForeground(1, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        val points =
            intent?.getParcelableArrayListExtra<LatLng>("TRACK_POINTS")

        if (points.isNullOrEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }

        Thread {
            try {
                val bitmap = downloadStaticMap(points)
                if (bitmap != null) {
                    val copy = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                    drawOverlay(copy, points.last())
                    saveBitmap(copy)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            stopSelf()

        }.start()

        return START_NOT_STICKY
    }

    // =====================================================
    // STATIC MAP DOWNLOAD
    // =====================================================

    private fun downloadStaticMap(points: List<LatLng>): Bitmap? {

        val path = buildPolyline(points)

        val apiKey = "AIzaSyC3bvvXeEHuQIoTb5d0AT8zh4zw7dAPfX4"

        val urlString =
            "https://maps.googleapis.com/maps/api/staticmap" +
                    "?size=1080x1920" +
                    "&maptype=roadmap" +
                    "&path=color:0x000000ff|weight:5|$path" +
                    "&markers=color:red|${points.last().latitude},${points.last().longitude}" +
                    "&key=$apiKey"

        android.util.Log.d("STATIC_MAP", urlString)

        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.connect()

        val responseCode = connection.responseCode
        android.util.Log.d("STATIC_MAP", "Response code: $responseCode")

        if (responseCode != 200) {
            val errorStream = connection.errorStream
            val errorText = errorStream?.bufferedReader()?.readText()
            android.util.Log.e("STATIC_MAP", "Error: $errorText")
            return null
        }

        val input = connection.inputStream
        return BitmapFactory.decodeStream(input)
    }

    private fun buildPolyline(points: List<LatLng>): String {
        return points.joinToString("|") {
            "${it.latitude},${it.longitude}"
        }
    }

    // =====================================================
    // OVERLAY TESTO
    // =====================================================

    private fun drawOverlay(bitmap: Bitmap, last: LatLng) {

        val canvas = Canvas(bitmap)

        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = 50f
            isAntiAlias = true
        }

        val bgPaint = Paint().apply {
            color = Color.WHITE
            alpha = 220
        }

        val date =
            SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                .format(Date())

        val infoText = """
Tracking completato
$date
Lat: ${last.latitude}
Lon: ${last.longitude}
        """.trimIndent()

        val lines = infoText.split("\n")
        val padding = 40f
        val lineHeight = 60f
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

    // =====================================================
    // SAVE IMAGE
    // =====================================================

    private fun saveBitmap(bitmap: Bitmap) {

        val filename =
            "tracking_${System.currentTimeMillis()}.jpg"

        val resolver = contentResolver

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/SmsGpsTracker"
            )
        }

        val uri = resolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        )

        uri?.let {
            resolver.openOutputStream(it)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
        }
    }

    // =====================================================
    // NOTIFICATION CHANNEL
    // =====================================================

    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Export Channel",
                NotificationManager.IMPORTANCE_LOW
            )

            val manager =
                getSystemService(NotificationManager::class.java)

            manager.createNotificationChannel(channel)
        }
    }
}

