package com.example.smsgpstracker.rxmulti

import android.content.Context
import android.util.Log

object RxPersistence {

    private const val PREF_NAME = "rx_multi_store"
    private const val KEY_TRACK = "track_points"
    private const val KEY_SESSION = "session_id"

    // ================================
    // 💾 SAVE TRACK
    // ================================
    fun saveTrack(context: Context, sessionId: String, points: List<Pair<Double, Double>>) {

        try {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

            val serialized = points.joinToString(";") {
                "${it.first},${it.second}"
            }

            prefs.edit()
                .putString(KEY_TRACK, serialized)
                .putString(KEY_SESSION, sessionId)
                .apply()

            Log.d("RX_PERSIST", "Saved ${points.size} points")

        } catch (e: Exception) {
            Log.e("RX_PERSIST", "Save error", e)
        }
    }

    // ================================
    // 📥 LOAD TRACK
    // ================================
    fun loadTrack(context: Context): Pair<String?, List<Pair<Double, Double>>> {

        return try {

            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

            val session = prefs.getString(KEY_SESSION, null)
            val serialized = prefs.getString(KEY_TRACK, "") ?: ""

            val points = serialized.split(";")
                .mapNotNull {
                    val parts = it.split(",")
                    if (parts.size == 2) {
                        try {
                            Pair(parts[0].toDouble(), parts[1].toDouble())
                        } catch (e: Exception) {
                            null
                        }
                    } else null
                }

            Log.d("RX_PERSIST", "Loaded ${points.size} points")

            Pair(session, points)

        } catch (e: Exception) {
            Log.e("RX_PERSIST", "Load error", e)
            Pair(null, emptyList())
        }
    }

    // ================================
    // 🧹 CLEAR
    // ================================
    fun clear(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        Log.d("RX_PERSIST", "Cleared")
    }
}