package com.example.smsgpstracker.rxmulti
import com.example.smsgpstracker.SmsCrc
import android.util.Base64

class RxMultiSmsParser {

    fun parse(sms: String): RxMultiSmsPacket? {

        if (!sms.startsWith("TX|")) return null

        val parts = sms.split('|', limit = 6)
        android.util.Log.d("RX_MULTI_DEBUG", "SMS RAW: $sms")
        android.util.Log.d("RX_MULTI_DEBUG", "PARTS SIZE: ${parts.size}")

        if (parts.size < 6) return null

        val tx = parts[0]
        val sessionId = parts[1]
        val seq = parts[2].toIntOrNull() ?: return null
        val type = parts[3]
        val payloadEncoded = parts[4].trim()
        val crc = parts[5].trim()
        android.util.Log.d("RX_MULTI_DEBUG", "SESSION: $sessionId SEQ: $seq TYPE: [$type]")

        val raw = "$tx|$sessionId|$seq|$type|$payloadEncoded"

        val calc = SmsCrc.crc8(raw)

        if (!calc.equals(crc, true)) {
            android.util.Log.w("RX_MULTI", "CRC FAIL seq=$seq")
            return null
        }

        // 🔥 DECODE SOLO DOPO CRC OK
        val payload = try {
            decodeBase64(payloadEncoded)
        } catch (e: Exception) {
            android.util.Log.e("RX_MULTI", "BASE64 DECODE FAIL seq=$seq")
            return null
        }

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