package com.example.smsgpstracker.rxmulti
import com.example.smsgpstracker.tx.PolylineCodec

class RxMultiTrackAssembler {

    data class SessionBuffer(
        val packets: MutableMap<Int, RxMultiSmsPacket> = mutableMapOf(),
        var isFinalReceived: Boolean = false,
        var lastUpdate: Long = System.currentTimeMillis()
    )

    private val sessions = mutableMapOf<String, SessionBuffer>()

    fun process(packet: RxMultiSmsPacket): List<Pair<Double, Double>>? {

        val session = sessions.getOrPut(packet.sessionId) {
            SessionBuffer()
        }

        session.lastUpdate = System.currentTimeMillis()

        // anti-duplicati
        if (!session.packets.containsKey(packet.seq)) {
            session.packets[packet.seq] = packet
        }

        if (packet.type == "F") {
            session.isFinalReceived = true
        }

        if (!session.isFinalReceived) {
            return null
        }

        // prova ricostruzione
        return tryReconstruct(packet.sessionId)
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