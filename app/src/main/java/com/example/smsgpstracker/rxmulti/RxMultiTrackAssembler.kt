package com.example.smsgpstracker.rxmulti

import android.util.Base64
import android.util.Log
import com.example.smsgpstracker.tx.PolylineCodec
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

class RxMultiTrackAssembler {

    //======================================================
    // SEGMENT
    //======================================================

    data class SegmentBuffer(

        val chunks:
        MutableMap<Int,String> =
            mutableMapOf(),

        var totalChunks:Int=-1,

        var startPointId:Long=-1,

        var endPointId:Long=-1,

        var finalReceived:Boolean=false,

        var completed:Boolean=false,

        var rebuildDone:Boolean=false,

        var lastUpdate:Long=
            System.currentTimeMillis()
    )

    //======================================================
    // SESSION
    //======================================================

    data class SessionBuffer(

        val segments:
        MutableMap<Long,SegmentBuffer> =
            mutableMapOf(),

        val fullTrack:
        MutableList<Pair<Double,Double>> =
            mutableListOf(),

        var lastPoint:
        Pair<Double,Double>?=
            null,

        var lastEndPointId:Long=-1,

        var finalReceived:Boolean=false,

        var finalSegmentId:Long=-1
    )

    //======================================================

    private val sessions =
        ConcurrentHashMap<
                String,
                SessionBuffer>()

    //======================================================
    // PROCESS
    //======================================================

    fun process(
        packet:
        RxMultiSmsPacket
    ) {

        cleanupOldSessions()

        val session =
            sessions.getOrPut(
                packet.sessionId
            ) {

                Log.e(
                    "ASM",
                    "NEW SESSION"
                )

                SessionBuffer()
            }

        val segment =
            session.segments.getOrPut(
                packet.segmentId
            ) {

                Log.e(
                    "ASM",
                    "NEW SEGMENT ${packet.segmentId}"
                )

                SegmentBuffer()
            }

        segment.lastUpdate =
            System.currentTimeMillis()

        if (
            segment.chunks.containsKey(
                packet.seq
            )
        ) {

            Log.d(
                "ASM_DUP",
                "DROP DUP"
            )

            return
        }

        segment.chunks[
            packet.seq
        ] =
            packet.payloadChunk

        segment.totalChunks =
            packet.total

        segment.startPointId =
            packet.startPointId

        segment.endPointId =
            packet.endPointId

        if (
            packet.type=="F"
        ) {

            segment.finalReceived =
                true

            session.finalSegmentId =
                packet.segmentId
        }

        if (
            !isSegmentComplete(
                segment
            )
        ) {

            return
        }

        if (
            segment.rebuildDone
        ) {

            return
        }

        rebuildSegment(
            session,
            segment
        )
    }

    //======================================================

    private fun isSegmentComplete(
        segment:
        SegmentBuffer
    ):Boolean {

        if (
            segment.totalChunks<=0
        ) {

            return false
        }

        for (
        i in 0 until
                segment.totalChunks
        ) {

            if (
                !segment.chunks.containsKey(
                    i
                )
            ) {

                return false
            }
        }

        return true
    }

    //======================================================

    private fun rebuildSegment(

        session:
        SessionBuffer,

        segment:
        SegmentBuffer

    ) {

        try {

            segment.rebuildDone =
                true

            if (
                session.lastEndPointId>0 &&
                segment.startPointId>
                session.lastEndPointId+5
            ) {

                Log.e(
                    "ASM_GAP",
                    "DROP DISCONTINUITY"
                )

                return
            }

            val builder =
                StringBuilder()

            for (
            i in 0 until
                    segment.totalChunks
            ) {

                val chunk =
                    segment.chunks[i]
                        ?: return

                builder.append(
                    chunk
                )
            }

            val decoded =
                String(

                    Base64.decode(

                        builder.toString(),

                        Base64.URL_SAFE
                                or
                                Base64.NO_WRAP

                    ),

                    Charsets.UTF_8
                )

            val points =
                PolylineCodec.decode(
                    decoded
                )

            if (
                points.isEmpty()
            ) {

                return
            }

            appendPoints(
                session,
                points
            )

            session.lastEndPointId =
                segment.endPointId

            segment.completed =
                true

            if (
                segment.finalReceived
            ) {

                session.finalReceived =
                    true
            }

            Log.e(
                "ASM_OK",

                "points=${session.fullTrack.size}"
            )

        }

        catch (
            e:Exception
        ) {

            Log.e(
                "ASM_ERR",
                "rebuild",
                e
            )

            segment.rebuildDone =
                false
        }
    }

    //======================================================

    private fun appendPoints(

        session:
        SessionBuffer,

        points:
        List<Pair<Double,Double>>

    ) {

        for (
        p in points
        ) {

            val last =
                session.fullTrack
                    .lastOrNull()

            if (

                last!=null &&

                abs(
                    last.first-
                            p.first
                )<1e-6 &&

                abs(
                    last.second-
                            p.second
                )<1e-6

            ) {

                continue
            }

            session.fullTrack.add(
                p
            )
        }

        session.lastPoint =
            session.fullTrack
                .lastOrNull()
    }

    //======================================================

    fun getFullTrack(
        sessionId:String
    ):List<Pair<Double,Double>> {

        return sessions[
            sessionId
        ]
            ?.fullTrack
            ?.toList()

            ?: emptyList()
    }

    //======================================================

    fun buildFinalTrack(
        sessionId:String
    ):List<Pair<Double,Double>>? {

        val session =
            sessions[
                sessionId
            ]

                ?: return null

        if (
            !session.finalReceived
        ) {

            return null
        }

        return session.fullTrack
            .toList()
    }

    //======================================================

    fun isComplete(
        sessionId:String
    ):Boolean {

        return sessions[
            sessionId
        ]

            ?.finalReceived

            ?: false
    }

    //======================================================

    private fun cleanupOldSessions() {

        val now =
            System.currentTimeMillis()

        val it =
            sessions.entries
                .iterator()

        while (
            it.hasNext()
        ) {

            val e =
                it.next()

            var newest =
                0L

            for (
            s in
            e.value
                .segments
                .values
            ) {

                newest =
                    maxOf(
                        newest,
                        s.lastUpdate
                    )
            }

            if (

                newest>0 &&

                now-newest
                >
                12L*
                60*
                60*
                1000

            ) {

                it.remove()
            }
        }
    }

    //======================================================

    fun reset() {

        sessions.clear()

        Log.e(
            "ASM",
            "RESET"
        )
    }
}