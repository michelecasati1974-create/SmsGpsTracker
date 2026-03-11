package com.example.smsgpstracker

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

private val memoryBuffer = mutableListOf<GpsPoint>()

data class GpsPoint(
    val timestamp: Long,
    val lat: Double,
    val lon: Double,
    val acc: Float
)

class GpsTrackBuffer(context: Context) {



    private val prefs: SharedPreferences =
        context.getSharedPreferences("TRACK_BUFFER", Context.MODE_PRIVATE)

    private val KEY_BUFFER = "gps_buffer"
    private val MAX_POINTS = 400

    fun addPoint(point: GpsPoint) {

        memoryBuffer.add(point)

        if (memoryBuffer.size > MAX_POINTS) {
            memoryBuffer.removeAt(0)
        }

        // salvataggio asincrono ogni 10 punti
        if (memoryBuffer.size % 10 == 0) {
            saveToPrefs()
        }
    }

    fun getPoints(): List<GpsPoint> {

        if (memoryBuffer.isNotEmpty()) {
            return memoryBuffer
        }

        val array = getArray()

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


        return memoryBuffer
    }

    private fun saveToPrefs() {

        val array = JSONArray()

        for (p in memoryBuffer) {

            val obj = JSONObject()

            obj.put("t", p.timestamp)
            obj.put("la", p.lat)
            obj.put("lo", p.lon)
            obj.put("ac", p.acc)

            array.put(obj)
        }

        saveArray(array)
    }

    fun clear() {
        prefs.edit().remove(KEY_BUFFER).apply()
    }

    fun count(): Int {
        return getArray().length()
    }

    private fun getArray(): JSONArray {

        val str = prefs.getString(KEY_BUFFER, "[]") ?: "[]"

        return JSONArray(str)
    }

    private fun saveArray(array: JSONArray) {

        prefs.edit()
            .putString(KEY_BUFFER, array.toString())
            .apply()
    }
}