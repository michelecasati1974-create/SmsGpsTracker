package com.example.smsgpstracker

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import com.example.smsgpstracker.repository.GpsTrackRepository
import java.text.SimpleDateFormat
import java.util.*

class RxActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rx)

        listView = findViewById(R.id.listGps)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1)
        listView.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    private fun refreshList() {
        adapter.clear()

        val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

        GpsTrackRepository.getAllPoints().forEach {
            val time = formatter.format(Date(it.timestamp))
            adapter.add(
                "$time | ${it.sender}\nLat: ${it.latitude}  Lon: ${it.longitude}"
            )
        }
    }
}

