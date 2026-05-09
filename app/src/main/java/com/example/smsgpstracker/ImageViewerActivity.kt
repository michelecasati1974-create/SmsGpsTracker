package com.example.smsgpstracker

import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

// 🔥 (consigliato) Glide
import com.bumptech.glide.Glide

class ImageViewerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_viewer)

        val imageView = findViewById<ImageView>(R.id.fullImage)

        val uriString = intent.getStringExtra("image_uri")

        if (uriString != null) {

            val uri = Uri.parse(uriString)

            // ✅ VERSIONE BASE (funziona sempre)
            // imageView.setImageURI(uri)

            // 🔥 VERSIONE MIGLIORE (consigliata)
            Glide.with(this)
                .load(uri)
                .into(imageView)
        }
    }
}