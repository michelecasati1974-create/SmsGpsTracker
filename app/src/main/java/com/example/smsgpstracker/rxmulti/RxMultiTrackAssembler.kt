package com.example.smsgpstracker.rxmulti

import android.util.Log
import com.example.smsgpstracker.tx.PolylineCodec

class RxMultiTrackAssembler {

    data class SessionBuffer(
        val packets: MutableMap<Int, RxMultiSmsPacket> = mutableMapOf(),
        var isFinalReceived: Boolean = false,
        var lastUpdate: Long = System.currentTimeMillis()
    )

    private val sessions = mutableMapOf<String, SessionBuffer>()

    fun process(packet: RxMultiSmsPacket): List<Pair<Double, Double>> {

        Log.e("ASM_IN",
            "SESSION=${packet.sessionId} SEQ=${packet.seq} TYPE=${packet.type}"
        )

        val session = sessions.getOrPut(packet.sessionId) {
            Log.e("ASM_NEW_SESSION", "Nuova sessione ${packet.sessionId}")
            SessionBuffer()
        }

        session.lastUpdate = System.currentTimeMillis()

        // anti duplicati
        if (!session.packets.containsKey(packet.seq)) {
            session.packets[packet.seq] = packet
        } else {
            Log.e("ASM_DUPLICATE", "SEQ duplicato ${packet.seq}")
        }

        Log.e("ASM_BUFFER",
            "BUFFER SIZE=${session.packets.size} KEYS=${session.packets.keys}"
        )

        // =========================
        // 📡 REALTIME
        // =========================
        val decodedNow = try {
            val pts = PolylineCodec.decode(packet.payload)
            Log.e("ASM_DECODE_NOW", "SEQ=${packet.seq} POINTS=${pts.size}")
            pts
        } catch (e: Exception) {
            Log.e("ASM_DECODE_FAIL", "Errore decode seq=${packet.seq}", e)
            emptyList()
        }

        // =========================
        // 🏁 FINALE
        // =========================
        if (packet.type == "F") {
            Log.e("ASM_FINAL", "Ricevuto FINALE")
            session.isFinalReceived = true
        }

        // =========================
        // 🔥 TENTA RICOSTRUZIONE
        // =========================
        if (session.isFinalReceived) {

            val full = tryReconstruct(packet.sessionId)

            if (full != null) {
                Log.e("ASM_RECONSTRUCT_OK",
                    "RICOSTRUZIONE COMPLETA punti=${full.size}"
                )
                return full
            } else {
                Log.e("ASM_RECONSTRUCT_FAIL",
                    "FINAL ricevuto ma sequenza incompleta"
                )
            }
        }

        return decodedNow
    }

    private fun tryReconstruct(sessionId: String): List<Pair<Double, Double>>? {

        val session = sessions[sessionId] ?: return null

        val sorted = session.packets.toSortedMap()

        Log.e("ASM_RECONSTRUCT_START",
            "SESSION=$sessionId KEYS=${sorted.keys}"
        )

        var expected = sorted.keys.firstOrNull() ?: return null

        for (key in sorted.keys) {

            Log.e("ASM_SEQ_CHECK",
                "KEY=$key EXPECTED=$expected"
            )

            if (key != expected) {
                Log.e("ASM_SEQ_BREAK",
                    "BUCO SEQUENZA → expected=$expected got=$key"
                )
                return null
            }

            expected++
        }

        val allPoints = mutableListOf<Pair<Double, Double>>()

        for ((seq, p) in sorted) {

            val decodedPart = try {
                val pts = PolylineCodec.decode(p.payload)
                Log.e("ASM_DECODE_FULL",
                    "SEQ=$seq POINTS=${pts.size}"
                )
                pts
            } catch (e: Exception) {
                Log.e("ASM_DECODE_FULL_FAIL", "SEQ=$seq", e)
                emptyList()
            }

            allPoints.addAll(decodedPart)
        }

        sessions.remove(sessionId)

        Log.e("ASM_SESSION_CLOSED", "Sessione rimossa")

        return allPoints
    }

    fun getPartialTrack(sessionId: String): List<Pair<Double, Double>> {

        val session = sessions[sessionId] ?: return emptyList()

        val sorted = session.packets.toSortedMap()

        val result = mutableListOf<Pair<Double, Double>>()

        var expected = sorted.keys.firstOrNull() ?: return emptyList()

        Log.e("ASM_PARTIAL_START",
            "SESSION=$sessionId START_EXPECTED=$expected KEYS=${sorted.keys}"
        )

        for ((seq, packet) in sorted) {

            Log.e("ASM_PARTIAL_CHECK",
                "SEQ=$seq EXPECTED=$expected"
            )

            if (seq != expected) {
                Log.e("ASM_PARTIAL_BREAK",
                    "STOP → buco sequenza seq=$seq expected=$expected"
                )
                break
            }

            val decoded = try {
                val pts = PolylineCodec.decode(packet.payload)
                Log.e("ASM_PARTIAL_DECODE",
                    "SEQ=$seq POINTS=${pts.size}"
                )
                pts
            } catch (e: Exception) {
                Log.e("ASM_PARTIAL_DECODE_FAIL", "SEQ=$seq", e)
                emptyList()
            }

            result.addAll(decoded)

            expected++
        }

        Log.e("ASM_PARTIAL_RESULT", "TOTAL POINTS=${result.size}")

        return result
    }
}