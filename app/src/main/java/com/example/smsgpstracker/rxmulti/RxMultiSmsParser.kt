package com.example.smsgpstracker.rxmulti

import com.example.smsgpstracker.SmsCrc
import android.util.Base64
import android.util.Log

class RxMultiSmsParser {

    fun parse(sms: String): RxMultiSmsPacket? {

        Log.e("PARSER_IN", "RAW SMS: [$sms]")

        if (!sms.startsWith("TX|")) {
            Log.e("PARSER_DROP", "Non è TX")
            return null
        }

        val parts = sms.split('|', limit = 6)

        Log.e("PARSER_SPLIT", "Parts size=${parts.size}")

        if (parts.size < 6) {
            Log.e("PARSER_ERROR", "Formato non valido")
            return null
        }

        val tx = parts[0]
        val sessionId = parts[1]
        val seq = parts[2].toIntOrNull()

        if (seq == null) {
            Log.e("PARSER_ERROR", "SEQ non numerico: ${parts[2]}")
            return null
        }

        val type = parts[3]
        val payloadEncoded = parts[4].trim()
        val crc = parts[5].trim()

        Log.e("PARSER_FIELDS",
            "SESSION=$sessionId SEQ=$seq TYPE=$type PAYLOAD_LEN=${payloadEncoded.length}"
        )

        val raw = "$tx|$sessionId|$seq|$type|$payloadEncoded"
        val calc = SmsCrc.crc8(raw)

        Log.e("PARSER_CRC",
            "CALC=$calc RX=$crc MATCH=${calc.equals(crc, true)}"
        )

        if (!calc.equals(crc, true)) {
            Log.e("PARSER_CRC_FAIL", "CRC FAIL → SCARTATO seq=$seq")
            return null
        }

        val payload = try {
            decodeBase64(payloadEncoded)
        } catch (e: Exception) {
            android.util.Log.e("PARSER", "BASE64 DECODE FAIL seq=$seq", e)
            return null
        }

        Log.e("PARSER_PAYLOAD", "USO DIRETTO len=${payload.length}")

        Log.e("PARSER_OK", "PACCHETTO VALIDO seq=$seq")

        return RxMultiSmsPacket(
            sessionId,
            seq,
            type,
            payload
        )
    }

    private fun decodeBase64(input: String): String {
        return String(
            Base64.decode(input, Base64.URL_SAFE or Base64.NO_WRAP),
            Charsets.UTF_8
        )
    }
}