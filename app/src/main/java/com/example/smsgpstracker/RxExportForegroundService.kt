package com.example.smsgpstracker

import android.app.*
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.location.Geocoder
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import com.google.android.gms.maps.model.LatLng
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import android.app.AlarmManager
import android.app.PendingIntent
import android.os.SystemClock

class RxExportForegroundService : Service() {

    private val CHANNEL_ID = "export_channel"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SmsGpsTracker")
            .setContentText("Generazione immagine tracking...")
            .setSmallIcon(android.R.drawable.ic_menu_mapmode)
            .setOngoing(true)
            .build()

        startForeground(1, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        val points =
            intent?.getParcelableArrayListExtra<LatLng>("TRACK_POINTS")

        if (points.isNullOrEmpty()) {
            showErrorNotification("Errore: nessun punto ricevuto")
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

                    showSuccessNotification()

                } else {
                    showErrorNotification("Errore: Bitmap NULL (API?)")
                }

            } catch (e: Exception) {
                showErrorNotification("Errore export: ${e.message}")
            }

            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()

        }.start()

        return START_STICKY
    }




    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)

        val restartServiceIntent =
            Intent(applicationContext, RxExportForegroundService::class.java)

        restartServiceIntent.setPackage(packageName)

        val pendingIntent = PendingIntent.getService(
            this,
            1001,
            restartServiceIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        alarmManager.set(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + 1000,
            pendingIntent
        )
    }

    // ===============================
    // GOOGLE STATIC MAP
    // ===============================

    private fun downloadStaticMap(points: List<LatLng>): Bitmap? {

        val path = points.joinToString("|") {
            "${it.latitude},${it.longitude}"
        }

        val apiKey = "AIzaSyC3bvvXeEHuQIoTb5d0AT8zh4zw7dAPfX4"

        val start = points.first()
        val end = points.last()

        val urlString =
            "https://maps.googleapis.com/maps/api/staticmap" +
                    "?size=1080x1920" +
                    "&maptype=roadmap" +
                    "&path=color:0x000000ff|weight:5|$path" +
                    "&markers=color:green|label:S|${start.latitude},${start.longitude}" +
                    "&markers=color:red|label:E|${end.latitude},${end.longitude}" +
                    "&key=$apiKey"

        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.connect()

        if (connection.responseCode != 200) {
            return null
        }

        val input: InputStream = connection.inputStream
        return BitmapFactory.decodeStream(input)
    }


    // ===============================
    // OVERLAY TESTO
    // ===============================

    private fun drawOverlay(bitmap: Bitmap, last: LatLng) {

        val canvas = Canvas(bitmap)


        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 16f
            isAntiAlias = true
            setShadowLayer(3f, 1f, 1f, Color.WHITE)
        }

        val date = SimpleDateFormat(
            "dd/MM/yyyy HH:mm:ss",
            Locale.getDefault()
        ).format(Date())

        var city = ""
        var province = ""

        try {
            val geocoder = Geocoder(this, Locale.getDefault())
            val addresses = geocoder.getFromLocation(
                last.latitude,
                last.longitude,
                1
            )

            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                city = addr.locality ?: ""
                province = addr.adminArea ?: ""
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }

        val textBlock = """
$city ${if (province.isNotEmpty()) "($province)" else ""}
$date
${"%.6f".format(last.latitude)}, ${"%.6f".format(last.longitude)}
""".trimIndent()

        var y = bitmap.height - 90f

        textBlock.split("\n").forEach {
            canvas.drawText(it, 40f, y, paint)
            y += 24f
        }
    }

    // ===============================
    // SAVE IMAGE
    // ===============================

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
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues
        )

        uri?.let {
            resolver.openOutputStream(it)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
        }
    }

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

    private fun showErrorNotification(message: String) {

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SmsGpsTracker - ERRORE")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(2, notification)
    }

    private fun showSuccessNotification() {

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SmsGpsTracker")
            .setContentText("Immagine tracking salvata")
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(3, notification)
    }

}


