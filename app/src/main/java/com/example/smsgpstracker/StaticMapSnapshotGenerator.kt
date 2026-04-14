package com.example.smsgpstracker.snapshot

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import java.io.File
import java.io.FileOutputStream
import java.net.URL

class StaticMapSnapshotGenerator(private val apiKey: String) {

    fun generate(
        trackPoints: List<LatLng>,
        manualPoints: List<LatLng>,
        emergencyPoints: List<LatLng>,
        outputFile: File
    ) {

        if (trackPoints.isEmpty()) return

        try {

            val path = buildPath(trackPoints)
            val markers = buildMarkers(trackPoints, manualPoints, emergencyPoints)

            val url = """
                https://maps.googleapis.com/maps/api/staticmap
                ?size=1024x1024
                &maptype=roadmap
                $path
                $markers
                &key=$apiKey
            """.trimIndent().replace("\n", "")

            Log.d("STATIC_MAP", "URL length: ${url.length}")

            val connection = URL(url).openConnection()
            connection.connect()

            val input = connection.getInputStream()
            val bitmap = BitmapFactory.decodeStream(input)

            if (bitmap == null) {
                Log.e("STATIC_MAP", "Bitmap null")
                return
            }

            val out = FileOutputStream(outputFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.flush()
            out.close()

            Log.d("STATIC_MAP", "Snapshot salvato: ${outputFile.absolutePath}")

        } catch (e: Exception) {
            Log.e("STATIC_MAP", "Errore snapshot", e)
        }
    }

    private fun buildPath(points: List<LatLng>): String {

        val sb = StringBuilder()

        sb.append("&path=color:0x0000ff|weight:4")

        // 🔥 LIMITA punti (fondamentale)
        val simplified = if (points.size > 200) {
            points.filterIndexed { i, _ -> i % 3 == 0 }
        } else points

        simplified.forEach {
            sb.append("|${it.latitude},${it.longitude}")
        }

        return sb.toString()
    }

    private fun buildMarkers(
        track: List<LatLng>,
        manual: List<LatLng>,
        emergency: List<LatLng>
    ): String {

        val sb = StringBuilder()

        // START
        track.firstOrNull()?.let {
            sb.append("&markers=color:green|label:S|${it.latitude},${it.longitude}")
        }

        // END
        track.lastOrNull()?.let {
            sb.append("&markers=color:red|label:E|${it.latitude},${it.longitude}")
        }

        // MANUAL
        manual.forEach {
            sb.append("&markers=color:yellow|${it.latitude},${it.longitude}")
        }

        // EMERGENCY
        emergency.forEach {
            sb.append("&markers=color:red|${it.latitude},${it.longitude}")
        }

        return sb.toString()
    }
}