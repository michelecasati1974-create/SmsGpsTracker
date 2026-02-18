package com.example.smsgpstracker.cyclosm.tiles

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

object TileDownloader {

    private const val TILE_SIZE = 256

    suspend fun downloadTile(x: Int, y: Int, zoom: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                val urlString = "https://a.tile-cyclosm.openstreetmap.fr/cyclosm/$zoom/$x/$y.png"
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.setRequestProperty("User-Agent", "SmsGpsTracker-CyclOSM")
                connection.connect()

                val code = connection.responseCode
                if (code != 200) return@withContext null

                val input: InputStream = connection.inputStream
                BitmapFactory.decodeStream(input)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
}
