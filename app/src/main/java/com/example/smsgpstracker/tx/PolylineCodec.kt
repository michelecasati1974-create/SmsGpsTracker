package com.example.smsgpstracker.tx

import kotlin.math.roundToInt

object PolylineCodec {

    private const val SCALE = 1e4

    // ======================================
    // ENCODE
    // ======================================
    fun encode(points: List<Pair<Double, Double>>): String {

        var lastLat = 0
        var lastLon = 0

        val result = StringBuilder()

        for (p in points) {

            val lat = (p.first * SCALE).roundToInt()
            val lon = (p.second * SCALE).roundToInt()

            val dLat = lat - lastLat
            val dLon = lon - lastLon

            encodeValue(dLat, result)
            encodeValue(dLon, result)

            lastLat = lat
            lastLon = lon
        }

        return result.toString()
    }

    private fun encodeValue(value: Int, result: StringBuilder) {

        var v = if (value < 0) (value shl 1).inv() else value shl 1

        while (v >= 0x20) {

            val next = (0x20 or (v and 0x1f)) + 63
            result.append(next.toChar())
            v = v shr 5
        }

        v += 63
        result.append(v.toChar())
    }

    // ======================================
    // DECODE
    // ======================================
    fun decode(encoded: String): List<Pair<Double, Double>> {

        val points = mutableListOf<Pair<Double, Double>>()

        var lat = 0
        var lon = 0
        var index = 0

        while (index < encoded.length) {

            val resultLat = decodeValue(encoded, index)
            index = resultLat.second
            lat += resultLat.first

            val resultLon = decodeValue(encoded, index)
            index = resultLon.second
            lon += resultLon.first

            points.add(
                Pair(
                    lat / SCALE,
                    lon / SCALE
                )
            )
        }

        return points
    }

    private fun decodeValue(
        encoded: String,
        startIndex: Int
    ): Pair<Int, Int> {

        var index = startIndex
        var shift = 0
        var result = 0
        var b: Int

        do {

            b = encoded[index++].code - 63
            result = result or ((b and 0x1f) shl shift)
            shift += 5

        } while (b >= 0x20)

        val d = if ((result and 1) != 0) (result shr 1).inv() else result shr 1

        return Pair(d, index)
    }
}