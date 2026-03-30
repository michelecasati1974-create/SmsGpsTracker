package com.example.smsgpstracker.rxmulti
import com.example.smsgpstracker.SmsCrc

class RxMultiSmsParser {

    fun parse(sms: String): RxMultiSmsPacket? {

        if (!sms.startsWith("TX|")) return null

        val parts = sms.split("|", limit = 6)

        if (parts.size < 6) return null

        val tx = parts[0]
        val sessionId = parts[1]
        val seq = parts[2].toIntOrNull() ?: return null
        val type = parts[3]
        val payload = parts[4]
        val crc = parts[5]

        val raw = "$tx|$sessionId|$seq|$type|$payload"

        val calc = SmsCrc.crc8(raw)

        if (!calc.equals(crc, true)) {
            android.util.Log.w("RX_MULTI", "CRC FAIL seq=$seq")
            return null
        }

        return RxMultiSmsPacket(
            sessionId,
            seq,
            type,
            payload
        )
    }
}