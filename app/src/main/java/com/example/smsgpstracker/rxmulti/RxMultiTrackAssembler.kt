package com.example.smsgpstracker.rxmulti
import com.example.smsgpstracker.tx.PolylineCodec

class RxMultiTrackAssembler {

    data class SessionBuffer(
        val packets: MutableMap<Int, RxMultiSmsPacket> = mutableMapOf(),
        var isFinalReceived: Boolean = false,
        var lastUpdate: Long = System.currentTimeMillis()
    )

    private val sessions = mutableMapOf<String, SessionBuffer>()

    fun isSessionComplete(sessionId: String): Boolean {

        val session = sessions[sessionId] ?: return false

        if (!session.isFinalReceived) return false

        val sorted = session.packets.toSortedMap()

        var expected = sorted.keys.firstOrNull() ?: return false

        for (key in sorted.keys) {
            if (key != expected) return false
            expected++
        }

        return true
    }

    fun hasMissingSequences(sessionId: String): Boolean {

        val session = sessions[sessionId] ?: return false

        val sorted = session.packets.toSortedMap()

        var expected = sorted.keys.firstOrNull() ?: return false

        for (key in sorted.keys) {
            if (key != expected) return true
            expected++
        }

        return false
    }



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
        }

        // 🔥 PROVA SEMPRE ricostruzione se F è arrivato
        if (session.isFinalReceived) {

            val full = tryReconstruct(packet.sessionId)

            if (full != null) {
                return full
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
    fun getPartialTrack(sessionId: String): List<Pair<Double, Double>> {

        val session = sessions[sessionId] ?: return emptyList()

        val sorted = session.packets.toSortedMap()

        val result = mutableListOf<Pair<Double, Double>>()

        var expected = sorted.keys.firstOrNull() ?: return emptyList()

        for ((seq, packet) in sorted) {

            if (seq != expected) {
                // 🔥 STOP su buco
                break
            }

            val decoded = PolylineCodec.decode(packet.payload)
            result.addAll(decoded)

            expected++
        }

        return result
    }
    fun isNewSession(packet: RxMultiSmsPacket): Boolean {
        return !sessions.containsKey(packet.sessionId)
    }
}