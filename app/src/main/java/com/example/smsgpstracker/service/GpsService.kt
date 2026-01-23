package com.example.smsgpstracker.service

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.smsgpstracker.location.LocationRepository
import com.example.smsgpstracker.sms.SmsSender

class GpsService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        val phone = intent?.getStringExtra("phone") ?: run {
            stopSelf()
            return START_NOT_STICKY
        }


        startForeground(1, createNotification())

        val repo = LocationRepository(this)

        repo.getCurrentLocation { location ->
            val msg = if (location != null) {
                "LAT=${location.latitude}\nLON=${location.longitude}"
            } else {
                "GPS NON DISPONIBILE"
            }

            SmsSender.send(this, phone, msg)
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun createNotification(): Notification {
        val channelId = "gps_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "GPS Service",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("GPS attivo")
            .setContentText("Invio posizione via SMS")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .build()
    }
}
