package com.example.smsgpstracker.cyclosm.overlay

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.google.android.gms.maps.model.LatLng
import com.example.smsgpstracker.cyclosm.tiles.TileUtils

object MarkerOverlay {

    fun drawMarker(
        bitmap: Bitmap,
        point: LatLng,
        latMin: Double, lonMin: Double,
        zoom: Int
    ) {
        val canvas = Canvas(bitmap)

        val paint = Paint().apply {
            color = Color.RED
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        // Calcola posizione pixel tile
        val (tileX, tileY) = TileUtils.latLonToTileXY(point.latitude, point.longitude, zoom)
        val xOffset = tileX * 256 - (tileX * 256) // semplice placeholder
        val yOffset = tileY * 256 - (tileY * 256)

        // Disegna marker
        canvas.drawCircle(
            xOffset.toFloat() + 128f,
            yOffset.toFloat() + 128f,
            12f,
            paint
        )
    }
}
