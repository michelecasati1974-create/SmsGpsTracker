package com.example.smsgpstracker.cyclosm.tiles

import kotlin.math.*

object TileUtils {

    fun latLonToTileXY(lat: Double, lon: Double, zoom: Int): Pair<Int, Int> {
        val latRad = Math.toRadians(lat)
        val n = 2.0.pow(zoom.toDouble())
        val x = ((lon + 180.0) / 360.0 * n).toInt()
        val y = ((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n).toInt()
        return x to y
    }

    fun boundingBoxTiles(
        latMin: Double, lonMin: Double,
        latMax: Double, lonMax: Double,
        zoom: Int
    ): List<Pair<Int, Int>> {
        val (xMin, yMax) = latLonToTileXY(latMin, lonMin, zoom)
        val (xMax, yMin) = latLonToTileXY(latMax, lonMax, zoom)

        val tiles = mutableListOf<Pair<Int, Int>>()
        for (x in xMin..xMax) {
            for (y in yMin..yMax) {
                tiles.add(x to y)
            }
        }
        return tiles
    }
}
