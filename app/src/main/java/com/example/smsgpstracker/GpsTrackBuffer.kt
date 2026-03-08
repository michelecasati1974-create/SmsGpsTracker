package com.example.smsgpstracker

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

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

    fun addPoint(point: GpsPoint) {

        val array = getArray()

        val obj = JSONObject()
        obj.put("t", point.timestamp)
        obj.put("la", point.lat)
        obj.put("lo", point.lon)
        obj.put("ac", point.acc)

        array.put(obj)

        saveArray(array)
    }

    fun getPoints(): List<GpsPoint> {

        val list = mutableListOf<GpsPoint>()
        val array = getArray()

        for (i in 0 until array.length()) {

            val o = array.getJSONObject(i)

            list.add(
                com.example.smsgpstracker.GpsPoint(
                    o.getLong("t"),
                    o.getDouble("la"),
                    o.getDouble("lo"),
                    o.getDouble("ac").toFloat()
                )
            )
        }

        return list
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