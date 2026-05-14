package com.example.smsgpstracker.rxmulti

import android.util.Base64
import android.util.Log
import com.example.smsgpstracker.tx.PolylineCodec
import java.util.concurrent.ConcurrentHashMap

class RxMultiTrackAssembler {

    // =====================================================
    // SEGMENT BUFFER
    // =====================================================

    data class SegmentBuffer(

        val chunks:
        MutableMap<Int, String> = mutableMapOf(),

        var totalChunks: Int = -1,

        var finalReceived: Boolean = false,

        var completed: Boolean = false,

        var lastUpdate: Long =
            System.currentTimeMillis()
    )

    // =====================================================
    // SESSION BUFFER
    // =====================================================

    data class SessionBuffer(

        val segments:
        MutableMap<Long, SegmentBuffer> = mutableMapOf(),

        val fullTrack:
        MutableList<Pair<Double, Double>> = mutableListOf(),

        var lastPoint:
        Pair<Double, Double>? = null,

        var finalReceived: Boolean = false
    )

    // =====================================================

    private val sessions =
        ConcurrentHashMap<String, SessionBuffer>()

    private var currentSessionId: String? = null

    // =====================================================
    // PROCESS
    // =====================================================

    fun process(packet: RxMultiSmsPacket) {

        cleanupOldSessions()

        val sessionId =
            packet.sessionId

        val segmentId =
            packet.segmentId

        currentSessionId = sessionId

        val session =
            sessions.getOrPut(sessionId) {
                SessionBuffer()
            }

        val segment =
            session.segments.getOrPut(segmentId) {
                SegmentBuffer()
            }

        segment.lastUpdate =
            System.currentTimeMillis()

        // =================================================
        // DUPLICATI
        // =================================================

        if (segment.chunks.containsKey(packet.seq)) {

            Log.d(
                "ASM_DUP",
                "segment=$segmentId seq=${packet.seq}"
            )

            return
        }

        // =================================================
        // STORE CHUNK
        // =================================================

        segment.chunks[packet.seq] =
            packet.payloadChunk

        segment.totalChunks =
            packet.total

        if (packet.type == "F") {

            segment.finalReceived = true

            session.finalReceived = true

            Log.e(
                "ASM_FINAL",
                "FINAL segment=$segmentId"
            )
        }

        Log.d(
            "ASM_STORE",
            "segment=$segmentId seq=${packet.seq}/${packet.total}"
        )

        // =================================================
        // SEGMENT COMPLETE?
        // =================================================

        if (!isSegmentComplete(segment)) {
            return
        }

        // evita rebuild multipli
        if (segment.completed) {
            return
        }

        segment.completed = true

        rebuildSegment(
            session,
            segmentId,
            segment
        )
    }

    // =====================================================
    // COMPLETE CHECK
    // =====================================================

    private fun isSegmentComplete(
        segment: SegmentBuffer
    ): Boolean {

        if (segment.totalChunks <= 0) {
            return false
        }

        for (i in 0 until segment.totalChunks) {

            if (!segment.chunks.containsKey(i)) {

                Log.w(
                    "ASM_MISSING",
                    "missing seq=$i"
                )

                return false
            }
        }

        return true
    }

    // =====================================================
    // REBUILD SEGMENT
    // =====================================================

    private fun rebuildSegment(
        session: SessionBuffer,
        segmentId: Long,
        segment: SegmentBuffer
    ) {

        try {

            val builder =
                StringBuilder()

            // =============================================
            // RIORDINO
            // =============================================

            for (i in 0 until segment.totalChunks) {

                builder.append(
                    segment.chunks[i]
                )
            }

            val fullBase64 =
                builder.toString()

            Log.e(
                "ASM_REBUILD",
                "segment=$segmentId len=${fullBase64.length}"
            )

            // =============================================
            // BASE64 FULL DECODE
            // =============================================

            val decodedBytes =
                Base64.decode(
                    fullBase64,
                    Base64.URL_SAFE or Base64.NO_WRAP
                )

            val encodedPolyline =
                String(
                    decodedBytes,
                    Charsets.UTF_8
                )

            // =============================================
            // POLYLINE DECODE
            // =============================================

            val points =
                PolylineCodec.decode(
                    encodedPolyline
                )

            appendPoints(
                session,
                points
            )

            Log.e(
                "ASM_OK",
                "segment=$segmentId points=${points.size}"
            )

        } catch (e: Exception) {

            Log.e(
                "ASM_REBUILD_ERR",
                "segment=$segmentId",
                e
            )
        }
    }

    // =====================================================
    // APPEND SAFE
    // =====================================================

    private fun appendPoints(
        session: SessionBuffer,
        newPoints: List<Pair<Double, Double>>
    ) {

        if (newPoints.isEmpty()) {
            return
        }

        val cleaned =
            mutableListOf<Pair<Double, Double>>()

        var lastLocal:
                Pair<Double, Double>? = null

        for (p in newPoints) {

            if (
                lastLocal != null &&
                kotlin.math.abs(
                    lastLocal.first - p.first
                ) < 1e-6 &&
                kotlin.math.abs(
                    lastLocal.second - p.second
                ) < 1e-6
            ) {

                continue
            }

            cleaned.add(p)

            lastLocal = p
        }

        // =============================================
        // DEDUP GLOBALE
        // =============================================

        for (p in cleaned) {

            val last =
                session.fullTrack.lastOrNull()

            if (
                last != null &&
                kotlin.math.abs(last.first - p.first) < 1e-6 &&
                kotlin.math.abs(last.second - p.second) < 1e-6
            ) {

                continue
            }

            session.fullTrack.add(p)
        }

        session.lastPoint =
            session.fullTrack.lastOrNull()

        Log.e(
            "ASM_APPEND",
            "total=${session.fullTrack.size}"
        )
    }

    // =====================================================
    // GET TRACK
    // =====================================================

    fun getFullTrack():
            List<Pair<Double, Double>> {

        val sessionId =
            currentSessionId
                ?: return emptyList()

        val session =
            sessions[sessionId]
                ?: return emptyList()

        return session.fullTrack.toList()
    }

    // =====================================================
    // FINAL TRACK
    // =====================================================

    fun buildFinalTrack():
            List<Pair<Double, Double>>? {

        val sessionId =
            currentSessionId
                ?: return null

        val session =
            sessions[sessionId]
                ?: return null

        if (!session.finalReceived) {
            return null
        }

        return session.fullTrack.toList()
    }

    // =====================================================
    // COMPLETE
    // =====================================================

    fun isComplete(): Boolean {

        val sessionId =
            currentSessionId
                ?: return false

        val session =
            sessions[sessionId]
                ?: return false

        return session.finalReceived
    }

    // =====================================================
    // CLEANUP
    // =====================================================

    private fun cleanupOldSessions() {

        val now =
            System.currentTimeMillis()

        val iterator =
            sessions.entries.iterator()

        while (iterator.hasNext()) {

            val entry = iterator.next()

            val session =
                entry.value

            var newest = 0L

            for (segment in session.segments.values) {

                newest =
                    maxOf(
                        newest,
                        segment.lastUpdate
                    )
            }

            if (
                newest > 0 &&
                now - newest > 5 * 60 * 1000
            ) {

                Log.w(
                    "ASM_CLEAN",
                    "remove session=${entry.key}"
                )

                iterator.remove()
            }
        }
    }

    // =====================================================

    fun reset() {

        sessions.clear()

        currentSessionId = null

        Log.e(
            "ASM_RESET",
            "RESET COMPLETO"
        )
    }
}