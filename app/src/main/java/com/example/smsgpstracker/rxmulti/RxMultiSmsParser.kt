package com.example.smsgpstracker.rxmulti
import com.example.smsgpstracker.SmsCrc

class RxMultiSmsParser {

    fun parse(sms: String): RxMultiSmsPacket? {

        if (!sms.startsWith("T#")) return null

        val parts = sms.split('|')

        if (parts.size != 3) return null

        val header = parts[0]
        val polyline = parts[1]
        val crc = parts[2]

        val payload = "$header|$polyline"

        val calc = SmsCrc.crc8(payload)

        if (!calc.equals(crc, true)) {
            return null
        }

        val body = header.substringAfter("T#")

        val seq = body.substringBefore("/").toInt()

        val points = body.substringAfter("/").toInt()

        return RxMultiSmsPacket(
            seq,
            points,
            polyline
        )
    }
}