// RxMultiTrackAssembler.kt
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

        var rebuildDone: Boolean = false,

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

        var finalReceived: Boolean = false,

        var finalSegmentId: Long = -1L
    )

    // =====================================================

    private val sessions =
        ConcurrentHashMap<String, SessionBuffer>()

    // =====================================================
    // PROCESS
    // =====================================================

    fun process(packet: RxMultiSmsPacket) {

        cleanupOldSessions()

        val sessionId =
            packet.sessionId

        val segmentId =
            packet.segmentId

        val session =
            sessions.getOrPut(sessionId) {

                Log.e(
                    "ASM_SESSION",
                    "NEW SESSION $sessionId"
                )

                SessionBuffer()
            }

        val segment =
            session.segments.getOrPut(segmentId) {

                Log.e(
                    "ASM_SEGMENT",
                    "NEW SEGMENT $segmentId"
                )

                SegmentBuffer()
            }

        segment.lastUpdate =
            System.currentTimeMillis()

        // =================================================
        // DUPLICATE PROTECTION
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

        // =================================================
        // FINAL FLAG
        // =================================================

        if (packet.type == "F") {

            segment.finalReceived = true

            session.finalSegmentId =
                segmentId

            Log.e(
                "ASM_FINAL",
                "FINAL FLAG segment=$segmentId"
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

            Log.d(
                "ASM_WAIT",
                "segment=$segmentId incomplete"
            )

            return
        }

        // =================================================
        // REBUILD ONLY ONCE
        // =================================================

        if (segment.rebuildDone) {

            Log.d(
                "ASM_SKIP",
                "segment=$segmentId already rebuilt"
            )

            return
        }

        segment.rebuildDone = true

        rebuildSegment(
            sessionId,
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
        sessionId: String,
        session: SessionBuffer,
        segmentId: Long,
        segment: SegmentBuffer
    ) {

        try {

            val builder =
                StringBuilder()

            // =============================================
            // ORDER CHUNKS
            // =============================================

            for (i in 0 until segment.totalChunks) {

                val chunk =
                    segment.chunks[i]

                if (chunk == null) {

                    Log.e(
                        "ASM_NULL",
                        "NULL chunk seq=$i"
                    )

                    return
                }

                builder.append(chunk)
            }

            val fullBase64 =
                builder.toString()

            Log.e(
                "ASM_REBUILD",
                "segment=$segmentId len=${fullBase64.length}"
            )

            Log.e(
                "ASM_SEGMENTS",
                "session=$sessionId segments=${session.segments.keys.sorted()}"
            )

            // =============================================
            // BASE64 DECODE
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

            Log.d(
                "ASM_POLY",
                "polylineLen=${encodedPolyline.length}"
            )

            // =============================================
            // POLYLINE DECODE
            // =============================================

            val points =
                PolylineCodec.decode(
                    encodedPolyline
                )

            if (points.isEmpty()) {

                Log.e(
                    "ASM_EMPTY",
                    "decoded points EMPTY"
                )

                return
            }

            appendPoints(
                session,
                points
            )

            segment.completed = true

            Log.e(
                "ASM_OK",
                "segment=$segmentId points=${points.size}"
            )

            // =============================================
            // FINAL COMPLETE
            // =============================================

            if (segment.finalReceived) {

                session.finalReceived = true

                Log.e(
                    "ASM_FINAL_OK",
                    "FINAL TRACK COMPLETE"
                )
            }

        } catch (e: Exception) {

            Log.e(
                "ASM_REBUILD_ERR",
                "segment=$segmentId",
                e
            )

            segment.rebuildDone = false
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

        // =================================================
        // LOCAL DEDUP
        // =================================================

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

        Log.e(
            "ASM_APPEND_DEBUG",
            "before=${session.fullTrack.size} add=${cleaned.size}"
        )

        // =================================================
        // GLOBAL DEDUP
        // =================================================

        for (p in cleaned) {

            val last =
                session.fullTrack.lastOrNull()

            if (
                last != null &&
                kotlin.math.abs(
                    last.first - p.first
                ) < 1e-6 &&
                kotlin.math.abs(
                    last.second - p.second
                ) < 1e-6
            ) {

                continue
            }

            session.fullTrack.add(p)
        }

        session.lastPoint =
            session.fullTrack.lastOrNull()

        Log.e(
            "ASM_APPEND_DEBUG",
            "after=${session.fullTrack.size}"
        )

        Log.e(
            "ASM_APPEND",
            "total=${session.fullTrack.size}"
        )
    }

    // =====================================================
    // GET TRACK
    // =====================================================

    fun getFullTrack(
        sessionId: String
    ): List<Pair<Double, Double>> {

        val session =
            sessions[sessionId]
                ?: return emptyList()

        return session.fullTrack.toList()
    }

    // =====================================================
    // FINAL TRACK
    // =====================================================

    fun buildFinalTrack(
        sessionId: String
    ): List<Pair<Double, Double>>? {

        val session =
            sessions[sessionId]
                ?: return null

        if (!session.finalReceived) {

            Log.w(
                "ASM_FINAL_WAIT",
                "final not received"
            )

            return null
        }

        return session.fullTrack.toList()
    }

    // =====================================================
    // COMPLETE
    // =====================================================

    fun isComplete(
        sessionId: String
    ): Boolean {

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

            val entry =
                iterator.next()

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

            // 🔥 12 ORE
            if (
                newest > 0 &&
                now - newest > 12 * 60 * 60 * 1000
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
    // RESET
    // =====================================================

    fun reset() {

        sessions.clear()

        Log.e(
            "ASM_RESET",
            "RESET COMPLETO"
        )
    }
}