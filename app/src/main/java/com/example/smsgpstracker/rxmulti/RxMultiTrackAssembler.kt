package com.example.smsgpstracker.rxmulti

import android.util.Base64
import android.util.Log
import com.example.smsgpstracker.tx.PolylineCodec

class RxMultiTrackAssembler {

    data class SessionBuffer(

        // seq ricevuti
        val receivedSeq: MutableSet<Int> = mutableSetOf(),

        // track completo
        val fullTrack: MutableList<Pair<Double, Double>> = mutableListOf(),

        // ultimo punto
        var lastPoint: Pair<Double, Double>? = null,

        // finale ricevuto
        var finalReceived: Boolean = false,

    )

    private val sessions = mutableMapOf<String, SessionBuffer>()

    private var currentSessionId: String? = null

    // =====================================================
    // PROCESS
    // =====================================================
    fun process(packet: RxMultiSmsPacket) {

        val sessionId = packet.sessionId

        // =========================================
        // NUOVA SESSIONE
        // =========================================
        if (currentSessionId != sessionId) {

            Log.e("ASM_SESSION", "NEW SESSION RESET")

            sessions.clear()

            currentSessionId = sessionId
        }

        val session = sessions.getOrPut(sessionId) {
            SessionBuffer()
        }

        // =========================================
        // DUPLICATI
        // =========================================
        if (session.receivedSeq.contains(packet.seq)) {

            Log.d("ASM_DUP", "SEQ ${packet.seq} ignorato")

            return
        }

        session.receivedSeq.add(packet.seq)

        // =========================================
        // DECODE SINGOLO SMS
        // =========================================
        try {

            val bytes = Base64.decode(
                packet.payloadChunk,
                Base64.URL_SAFE or Base64.NO_WRAP
            )

            val decoded =
                String(bytes, Charsets.UTF_8)

            val points =
                PolylineCodec.decode(decoded)

            appendPoints(session, points)

            Log.e(
                "ASM_APPEND",
                "seq=${packet.seq} points=${points.size} total=${session.fullTrack.size}"
            )

        } catch (e: Exception) {

            Log.e(
                "ASM_DECODE",
                "DECODE FAILED seq=${packet.seq}",
                e
            )
        }

        // =========================================
        // FINAL
        // =========================================
        if (packet.type == "F") {

            session.finalReceived = true

            Log.e(
                "ASM_FINAL",
                "FINAL RECEIVED total=${session.fullTrack.size}"
            )
        }
    }

    // =====================================================
    // APPEND SENZA DUPLICATI
    // =====================================================
    private fun appendPoints(
        session: SessionBuffer,
        newPoints: List<Pair<Double, Double>>
    ) {

        for (p in newPoints) {

            val last = session.lastPoint

            if (
                last != null &&
                kotlin.math.abs(last.first - p.first) < 1e-6 &&
                kotlin.math.abs(last.second - p.second) < 1e-6
            ) {
                continue
            }

            session.fullTrack.add(p)

            session.lastPoint = p
        }
    }

    // =====================================================
    fun getFullTrack(): List<Pair<Double, Double>> {

        val sessionId = currentSessionId
            ?: return emptyList()

        val session = sessions[sessionId]
            ?: return emptyList()

        return session.fullTrack.toList()
    }

    // =====================================================
    fun buildFinalTrack(): List<Pair<Double, Double>>? {

        val sessionId = currentSessionId
            ?: return null

        val session = sessions[sessionId]
            ?: return null

        if (!session.finalReceived) {
            return null
        }

        return session.fullTrack.toList()
    }

    // =====================================================
    fun isComplete(): Boolean {

        val sessionId = currentSessionId
            ?: return false

        val session = sessions[sessionId]
            ?: return false

        return session.finalReceived
    }

    // =====================================================
    fun reset() {

        sessions.clear()

        currentSessionId = null

        Log.e("ASM_RESET", "RESET COMPLETO")
    }
}