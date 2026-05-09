package com.example.smsgpstracker

import android.content.ContentUris
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.os.Build
import java.io.File
import android.os.Environment


class RxGalleryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private val imageList = mutableListOf<ImageItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)

        recyclerView = findViewById(R.id.recyclerView)

        recyclerView.layoutManager = GridLayoutManager(this, 2)

        recyclerView.setHasFixedSize(true)
        recyclerView.itemAnimator = null
        recyclerView.setItemViewCacheSize(20)

        loadImages()

        recyclerView.adapter = GalleryAdapter(imageList)
    }

    private fun loadImages() {

        imageList.clear()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            // ✅ Android moderno
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME
            )

            val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
            val selectionArgs = arrayOf("%Pictures/SMSTracker%")

            val cursor = contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )

            cursor?.use {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)

                while (it.moveToNext()) {
                    val id = it.getLong(idColumn)
                    val name = it.getString(nameColumn)

                    val uri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    imageList.add(ImageItem(uri.toString(), name))
                }
            }

        } else {

            // 🔥 Android 7 fallback
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "SMSTracker"
            )

            if (dir.exists()) {

                val files = dir.listFiles()

                files?.sortedByDescending { it.lastModified() }?.forEach { file ->
                    imageList.add(ImageItem(file.toURI().toString(), file.name))
                }
            }
        }
    }
}

// ================================
// DATA CLASS
// ================================
data class ImageItem(
    val uri: String,
    val name: String
)

// ================================
// ADAPTER
// ================================
class GalleryAdapter(private val items: List<ImageItem>) :
    RecyclerView.Adapter<GalleryAdapter.ViewHolder>() {

    class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.imageView)
        val text: TextView = view.findViewById(R.id.textView)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_image, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        holder.text.text = item.name

        val uri = android.net.Uri.parse(item.uri)
        holder.image.setImageURI(uri)
        holder.itemView.setOnClickListener {

            val context = holder.itemView.context
            val intent = Intent(context, ImageViewerActivity::class.java)

            intent.putExtra("image_uri", item.uri)

            context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int = items.size
}
