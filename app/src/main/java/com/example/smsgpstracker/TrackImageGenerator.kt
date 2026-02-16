package com.example.smsgpstracker

import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.content.ContentValues
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object TrackImageGenerator {

    fun generateAndSave(
        context: Context,
        trackPoints: List<LatLngData>
    ) {

        if (trackPoints.isEmpty()) return

        val width = 1080
        val height = 1080

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        canvas.drawColor(Color.WHITE)

        val paintLine = Paint().apply {
            color = Color.BLUE
            strokeWidth = 6f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }

        // Normalizzazione coordinate su canvas
        val lats = trackPoints.map { it.latitude }
        val lons = trackPoints.map { it.longitude }

        val minLat = lats.min()
        val maxLat = lats.max()
        val minLon = lons.min()
        val maxLon = lons.max()

        fun mapX(lon: Double) =
            ((lon - minLon) / (maxLon - minLon) * (width - 100) + 50).toFloat()

        fun mapY(lat: Double) =
            (height - ((lat - minLat) / (maxLat - minLat) * (height - 200) + 100)).toFloat()

        val path = Path()

        trackPoints.forEachIndexed { index, point ->
            val x = mapX(point.longitude)
            val y = mapY(point.latitude)

            if (index == 0) path.moveTo(x, y)
            else path.lineTo(x, y)
        }

        canvas.drawPath(path, paintLine)

        val last = trackPoints.last()

        val paintText = Paint().apply {
            color = Color.BLACK
            textSize = 40f
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
        }

        canvas.drawText(
            "Lat: ${last.latitude}",
            60f,
            height - 120f,
            paintText
        )

        canvas.drawText(
            "Lon: ${last.longitude}",
            60f,
            height - 70f,
            paintText
        )

        saveBitmap(context, bitmap)
    }

    private fun saveBitmap(context: Context, bitmap: Bitmap) {

        val time = SimpleDateFormat(
            "yyyyMMdd_HHmmss",
            Locale.getDefault()
        ).format(Date())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "TRACK_$time.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/SmsGpsTracker"
                )
            }

            val uri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values
            )

            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }
            }

        } else {

            val root = Environment.getExternalStorageDirectory()
            val directory = File(root, "Pictures/SmsGpsTracker")

            if (!directory.exists()) directory.mkdirs()

            val file = File(directory, "TRACK_$time.jpg")

            val out = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            out.flush()
            out.close()
        }
    }
}
data class LatLngData(
    val latitude: Double,
    val longitude: Double
)