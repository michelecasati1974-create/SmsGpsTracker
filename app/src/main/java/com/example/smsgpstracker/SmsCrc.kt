package com.example.smsgpstracker

object SmsCrc {

    fun crc8(input: String): String {

        var crc = 0

        for (c in input.toByteArray()) {
            crc = crc xor c.toInt()

            for (i in 0 until 8) {
                crc = if ((crc and 0x80) != 0) {
                    (crc shl 1) xor 0x07
                } else {
                    crc shl 1
                }
                crc = crc and 0xFF
            }
        }

        return String.format("%02X", crc)
    }
}