package com.example.smsgpstracker;

import android.graphics.Color;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import android.os.Handler;
import android.widget.TextView;
import android.util.Log;





public class DebugTrackActivity extends AppCompatActivity
        implements OnMapReadyCallback {
    public static boolean isOpen = false;
    private GoogleMap map;
    private TextView statsView;
    private Handler handler = new Handler();
    private boolean firstDraw = true;
    private boolean cameraMoved = false;
    private DebugGraphView graphView;
    private boolean mapReady = false;



    @Override
    protected void onCreate(Bundle savedInstanceState) {


        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_debug_track);

        isOpen = true;

        statsView = findViewById(R.id.stats);
        graphView = findViewById(R.id.graph);

        startStatsUpdater();

        SupportMapFragment mapFragment =
                (SupportMapFragment) getSupportFragmentManager()
                        .findFragmentById(R.id.map);

        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
        if (graphView != null) {
            graphView.invalidate();
        }
    }

    private void startStatsUpdater() {

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {

                refreshMap();
                updateStats();

                if (graphView != null) graphView.invalidate();

                handler.postDelayed(this, 1000);
            }
        }, 1000);
    }

    private void refreshMap() {
        if (!mapReady) return;
        redrawAll();
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {

        map = googleMap;
        mapReady = true;

        Log.d("DEBUG_TRACK", "Map READY → redraw");

        redrawAll();
        updateStats(); // 🔥 AGGIUNTA
    }



    private void redrawAll() {

        if (map == null) return;

        map.clear();

        // 🔴 RAW
        if (DebugTrackStore.raw != null && DebugTrackStore.raw.size() > 1) {
            map.addPolyline(
                    new PolylineOptions()
                            .addAll(DebugTrackStore.raw)
                            .color(Color.RED)
                            .width(4)
            );
        }

        // 🟡 FILTERED (AGGIUNTA!)
        if (DebugTrackStore.filtered != null && DebugTrackStore.filtered.size() > 1) {
            map.addPolyline(
                    new PolylineOptions()
                            .addAll(DebugTrackStore.filtered)
                            .color(Color.YELLOW)
                            .width(5)
            );
        }

        // 🟢 SIMPLIFIED
        if (DebugTrackStore.simplified != null && DebugTrackStore.simplified.size() > 1) {
            map.addPolyline(
                    new PolylineOptions()
                            .addAll(DebugTrackStore.simplified)
                            .color(Color.GREEN)
                            .width(6)
            );
        }

        moveCamera();
    }

    private void updateStats() {

        String text =
                "RAW: " + DebugTrackStore.rawCount + "\n" +
                        "FILTER: " + DebugTrackStore.filteredCount + "\n" +
                        "SIMPL: " + DebugTrackStore.simplifiedCount + "\n" +
                        "SMS LEN: " + DebugTrackStore.smsLength + "\n" +
                        "SMS: " + DebugTrackStore.lastSms;

        if (statsView != null) {
            statsView.setText(text);
        }
    }


    private void moveCamera() {

        if (cameraMoved) return;

        if (DebugTrackStore.raw == null || DebugTrackStore.raw.isEmpty()) return;

        LatLng last = DebugTrackStore.raw.get(DebugTrackStore.raw.size() - 1);

        map.moveCamera(CameraUpdateFactory.newLatLngZoom(last, 16f));

        cameraMoved = true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isOpen = false;
    }

}



