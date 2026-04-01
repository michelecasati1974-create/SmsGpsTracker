package com.example.smsgpstracker.rxmulti
import com.example.smsgpstracker.tx.PolylineCodec

class RxMultiTrackAssembler {

    data class SessionBuffer(
        val packets: MutableMap<Int, RxMultiSmsPacket> = mutableMapOf(),
        var isFinalReceived: Boolean = false,
        var lastUpdate: Long = System.currentTimeMillis()
    )

    private val sessions = mutableMapOf<String, SessionBuffer>()

    fun process(packet: RxMultiSmsPacket): List<Pair<Double, Double>> {

        val session = sessions.getOrPut(packet.sessionId) {
            SessionBuffer()
        }

        session.lastUpdate = System.currentTimeMillis()

        // anti-duplicati
        if (!session.packets.containsKey(packet.seq)) {
            session.packets[packet.seq] = packet
        }

        // =========================
        // 📡 REALTIME (SEMPRE)
        // =========================
        val decodedNow = PolylineCodec.decode(packet.payload)

        // =========================
        // 🏁 FINALE
        // =========================
        if (packet.type == "F") {

            session.isFinalReceived = true

            val full = tryReconstruct(packet.sessionId)

            if (full != null) {
                return full   // 🔥 traccia completa
            }
        }

        // =========================
        // 🔁 DEFAULT: ritorna segmento corrente
        // =========================
        return decodedNow
    }

    fun decodeSingle(packet: RxMultiSmsPacket): List<Pair<Double, Double>> {
        return PolylineCodec.decode(packet.payload)
    }

    private fun tryReconstruct(sessionId: String): List<Pair<Double, Double>>? {

        val session = sessions[sessionId] ?: return null

        val sorted = session.packets.toSortedMap()

        var expected = sorted.keys.firstOrNull() ?: return null

        for (key in sorted.keys) {
            if (key != expected) {
                android.util.Log.w(
                    "RX_MULTI",
                    "Missing packet: expected $expected got $key"
                )
                return null
            }
            expected++
        }

        // concat payload
        val allPoints = mutableListOf<Pair<Double, Double>>()

        for (p in sorted.values) {

            val decodedPart = PolylineCodec.decode(p.payload)

            allPoints.addAll(decodedPart)
        }

        sessions.remove(sessionId)

        return allPoints   // ✅ CORRETTO
    }
}