package com.example.smsgpstracker.rxmulti

import android.util.Log
import com.example.smsgpstracker.SmsCrc

class RxMultiSmsParser {

    fun parse(sms: String): RxMultiSmsPacket? {

        Log.d("PARSER_IN", "RAW=[$sms]")

        if (!sms.startsWith("TX|")) {
            return null
        }

        // TX|session|seq|type|payload|crc
        val parts = sms.split('|', limit = 6)

        if (parts.size != 6) {

            Log.e("PARSER_ERR", "Formato invalido parts=${parts.size}")
            return null
        }

        val sessionId = parts[1]

        val seq = parts[2].toIntOrNull()
            ?: return null

        val type = parts[3]

        val payload = parts[4].trim()

        val crc = parts[5].trim()

        // =========================
        // CRC CHECK
        // =========================
        val raw = "TX|$sessionId|$seq|$type|$payload"

        val calc = SmsCrc.crc8(raw)

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
            total = -1, // NON USATO PIU'
            type = type,
            payloadChunk = payload
        )
    }
}