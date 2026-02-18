package com.example.smsgpstracker.cyclosm.composer

import android.graphics.Bitmap
import android.graphics.Canvas
import com.example.smsgpstracker.cyclosm.tiles.TileDownloader
import com.example.smsgpstracker.cyclosm.tiles.TileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MapComposer {

    suspend fun composeMap(
        latMin: Double, lonMin: Double,
        latMax: Double, lonMax: Double,
        zoom: Int
    ): Bitmap? = withContext(Dispatchers.IO) {

        val tiles = TileUtils.boundingBoxTiles(latMin, lonMin, latMax, lonMax, zoom)
        if (tiles.isEmpty()) return@withContext null

        val width = tiles.map { it.first }.distinct().size * 256
        val height = tiles.map { it.second }.distinct().size * 256

        val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resultBitmap)

        val xOffset = tiles.map { it.first }.minOrNull() ?: 0
        val yOffset = tiles.map { it.second }.minOrNull() ?: 0

        tiles.forEach { (x, y) ->
            val tile = TileDownloader.downloadTile(x, y, zoom)
            tile?.let {
                val left = (x - xOffset) * 256
                val top = (y - yOffset) * 256
                canvas.drawBitmap(tile, left.toFloat(), top.toFloat(), null)
            }
        }

        return@withContext resultBitmap
    }
}
