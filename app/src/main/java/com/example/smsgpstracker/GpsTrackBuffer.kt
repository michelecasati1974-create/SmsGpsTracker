package com.example.smsgpstracker

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

data class GpsPoint(
    val timestamp: Long,
    val lat: Double,
    val lon: Double,
    val acc: Float
)

class GpsTrackBuffer(context: Context) {

    private val memoryBuffer = mutableListOf<GpsPoint>()

    private val prefs: SharedPreferences =
        context.getSharedPreferences("TRACK_BUFFER", Context.MODE_PRIVATE)

    private val KEY_BUFFER = "gps_buffer"

    // 🔥 HARD LIMIT RIDOTTO (evita OOM)
    private val MAX_POINTS = 1000

    // 🔥 controllo salvataggio
    private var saveCounter = 0

    // ================================
    // ADD POINT (SAFE)
    // ================================
    @Synchronized
    fun addPoint(point: GpsPoint) {

        memoryBuffer.add(point)

        // 🔴 HARD LIMIT
        if (memoryBuffer.size > MAX_POINTS) {
            memoryBuffer.removeAt(0)
        }

        saveCounter++

        // ✅ salva ogni 20 punti (NON ogni volta)
        if (saveCounter >= 20) {
            saveToPrefsSafe()
            saveCounter = 0
        }
    }

    // ================================
    // GET COPY
    // ================================
    @Synchronized
    fun getPointsCopy(): List<GpsPoint> {

        if (memoryBuffer.isNotEmpty()) {
            return ArrayList(memoryBuffer)
        }

        val array = getArray()

        memoryBuffer.clear()

        for (i in 0 until array.length()) {

            val o = array.getJSONObject(i)

            memoryBuffer.add(
                GpsPoint(
                    o.getLong("t"),
                    o.getDouble("la"),
                    o.getDouble("lo"),
                    o.getDouble("ac").toFloat()
                )
            )
        }

        return ArrayList(memoryBuffer)
    }

    // ================================
    // CLEAR
    // ================================
    @Synchronized
    fun clear() {
        memoryBuffer.clear()
        prefs.edit().remove(KEY_BUFFER).apply()
    }

    // ================================
    // COUNT
    // ================================
    @Synchronized
    fun count(): Int = memoryBuffer.size

    // ================================
    // 🔥 SAVE SAFE (LIMITATO)
    // ================================
    private fun saveToPrefsSafe() {

        val maxSave = 100 // 🔥 salva solo ultimi 100

        val limited = if (memoryBuffer.size > maxSave) {
            memoryBuffer.takeLast(maxSave)
        } else {
            memoryBuffer
        }

        try {
            saveToPrefsInternal(limited)
        } catch (e: Exception) {
            Log.e("GPS_BUFFER", "Errore save SAFE", e)
        }
    }

    // ================================
    // SAVE INTERNAL
    // ================================
    private fun saveToPrefsInternal(data: List<GpsPoint>) {

        val array = JSONArray()

        for (p in data) {
            val obj = JSONObject()

            obj.put("t", p.timestamp)
            obj.put("la", p.lat)
            obj.put("lo", p.lon)
            obj.put("ac", p.acc)

            array.put(obj)
        }

        prefs.edit()
            .putString(KEY_BUFFER, array.toString())
            .apply()
    }

    private fun getArray(): JSONArray {
        val str = prefs.getString(KEY_BUFFER, "[]") ?: "[]"
        return JSONArray(str)
    }
}