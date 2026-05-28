package com.example.smsgpstracker.rxmulti

import android.util.Log
import com.example.smsgpstracker.SmsCrc

class RxMultiSmsParser {

    fun parse(
        sms: String
    ): RxMultiSmsPacket? {

        Log.d(
            "PARSER_IN",
            "RAW=[$sms]"
        )

        if (!sms.startsWith("TX|")) {
            return null
        }

        // =====================================================
        // NUOVO FORMATO:
        //
        // TX|session|segment|startPoint|endPoint|
        // seq/total|type|payload|crc
        // =====================================================

        val parts =
            sms.split(
                '|',
                limit = 9
            )

        if (
            parts.size != 9
        ) {

            Log.e(
                "PARSER_ERR",
                "Formato invalido parts=${parts.size}"
            )

            return null
        }

        try {

            val sessionId =
                parts[1]

            val segmentId =
                parts[2].toLong()

            val startPointId =
                parts[3].toLong()

            val endPointId =
                parts[4].toLong()

            val seqParts =
                parts[5]
                    .split('/')

            if (
                seqParts.size != 2
            ) {

                Log.e(
                    "PARSER_ERR",
                    "SEQ/TOTAL invalido"
                )

                return null
            }

            val seq =
                seqParts[0]
                    .toInt()

            val total =
                seqParts[1]
                    .toInt()

            val type =
                parts[6]

            val payload =
                parts[7]

            val crc =
                parts[8]
                    .trim()

            // =================================================
            // CRC CHECK
            // =================================================

            val raw =

                "TX|" +

                        sessionId +

                        "|" +

                        segmentId +

                        "|" +

                        startPointId +

                        "|" +

                        endPointId +

                        "|" +

                        seq +

                        "/" +

                        total +

                        "|" +

                        type +

                        "|" +

                        payload

            val calc =
                SmsCrc.crc8(raw)

            if (
                !calc.equals(
                    crc,
                    true
                )
            ) {

                Log.e(
                    "PARSER_CRC_FAIL",
                    "segment=$segmentId " +
                            "seq=$seq " +
                            "calc=$calc " +
                            "rx=$crc"
                )

                return null
            }

            Log.d(
                "PARSER_OK",
                "seg=$segmentId " +
                        "points=$startPointId->$endPointId " +
                        "seq=$seq/$total"
            )

            return RxMultiSmsPacket(

                sessionId =
                    sessionId,

                segmentId =
                    segmentId,

                startPointId =
                    startPointId,

                endPointId =
                    endPointId,

                seq =
                    seq,

                total =
                    total,

                type =
                    type,

                payloadChunk =
                    payload
            )

        } catch (e: Exception) {

            Log.e(
                "PARSER_EX",
                "PARSE FAILED",
                e
            )

            return null
        }
    }
}