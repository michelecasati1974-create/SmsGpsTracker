package com.example.smsgpstracker.rxmulti

import android.util.Log
import com.example.smsgpstracker.SmsCrc

class RxMultiSmsParser {

    fun parse(sms: String): RxMultiSmsPacket? {

        Log.d("PARSER_IN", "RAW=[$sms]")

        if (!sms.startsWith("TX|")) {
            return null
        }

        // =====================================================
        // NUOVO FORMATO:
        //
        // TX|session|segmentId|seq/total|type|payload|crc
        // =====================================================

        val parts = sms.split('|', limit = 7)

        if (parts.size != 7) {

            Log.e(
                "PARSER_ERR",
                "Formato invalido parts=${parts.size}"
            )

            return null
        }

        try {

            val sessionId = parts[1]

            val segmentId =
                parts[2].toLong()

            val seqParts =
                parts[3].split('/')

            if (seqParts.size != 2) {

                Log.e(
                    "PARSER_ERR",
                    "SEQ/TOTAL invalido"
                )

                return null
            }

            val seq =
                seqParts[0].toInt()

            val total =
                seqParts[1].toInt()

            val type = parts[4]

            val payload = parts[5]

            val crc =
                parts[6].trim()

            // =================================================
            // CRC CHECK
            // =================================================

            val raw =
                "TX|" +
                        sessionId + "|" +
                        segmentId + "|" +
                        seq + "/" + total + "|" +
                        type + "|" +
                        payload

            val calc =
                SmsCrc.crc8(raw)

            if (!calc.equals(crc, true)) {

                Log.e(
                    "PARSER_CRC_FAIL",
                    "seq=$seq calc=$calc rx=$crc"
                )

                return null
            }

            return RxMultiSmsPacket(
                sessionId = sessionId,
                seq = seq,
                total = total,
                type = type,
                payloadChunk = payload,
                segmentId = segmentId
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