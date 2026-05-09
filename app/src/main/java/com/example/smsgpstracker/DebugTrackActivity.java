package com.example.smsgpstracker;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.maps.*;
import com.google.android.gms.maps.model.*;

public class DebugTrackActivity extends AppCompatActivity
        implements OnMapReadyCallback {

    public static boolean isOpen = false;

    private GoogleMap map;
    private TextView statsView;
    private Handler handler = new Handler();

    private boolean cameraMoved = false;
    private boolean mapReady = false;

    private DebugGraphView graphView;

    // 🔥 FIX LEAK
    private Polyline rawPolyline;
    private Polyline filteredPolyline;
    private Polyline simplifiedPolyline;

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
    }

    private void startStatsUpdater() {

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {

                refreshMap();
                updateStats();

                if (graphView != null) graphView.invalidate();

                handler.postDelayed(this, 2000); // 🔥 meno stress CPU
            }
        }, 1500);
    }

    private void refreshMap() {
        if (!mapReady) return;
        redrawAll();
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {

        map = googleMap;
        mapReady = true;

        Log.d("DEBUG_TRACK", "Map READY");

        redrawAll();
        updateStats();
    }

    private void redrawAll() {

        if (map == null) return;

        // =========================
        // 🔴 RAW
        // =========================
        if (DebugTrackStore.raw != null && DebugTrackStore.raw.size() > 1) {

            if (rawPolyline == null) {
                rawPolyline = map.addPolyline(
                        new PolylineOptions()
                                .addAll(limit(DebugTrackStore.raw))
                                .color(Color.RED)
                                .width(4)
                );
            } else {
                rawPolyline.setPoints(limit(DebugTrackStore.raw));
            }
        }

        // =========================
        // 🟡 FILTERED
        // =========================
        if (DebugTrackStore.filtered != null && DebugTrackStore.filtered.size() > 1) {

            if (filteredPolyline == null) {
                filteredPolyline = map.addPolyline(
                        new PolylineOptions()
                                .addAll(limit(DebugTrackStore.filtered))
                                .color(Color.YELLOW)
                                .width(5)
                );
            } else {
                filteredPolyline.setPoints(limit(DebugTrackStore.filtered));
            }
        }

        // =========================
        // 🟢 SIMPLIFIED
        // =========================
        if (DebugTrackStore.simplified != null && DebugTrackStore.simplified.size() > 1) {

            if (simplifiedPolyline == null) {
                simplifiedPolyline = map.addPolyline(
                        new PolylineOptions()
                                .addAll(limit(DebugTrackStore.simplified))
                                .color(Color.GREEN)
                                .width(6)
                );
            } else {
                simplifiedPolyline.setPoints(limit(DebugTrackStore.simplified));
            }
        }

        moveCamera();
    }

    // 🔥 LIMIT ANTI-OOM
    private java.util.List<LatLng> limit(java.util.List<LatLng> input) {

        int max = 120;

        if (input.size() <= max) return input;

        return input.subList(input.size() - max, input.size());
    }

    private void updateStats() {

        String text =
                "RAW: " + DebugTrackStore.rawCount + "\n" +
                        "FILTER: " + DebugTrackStore.filteredCount + "\n" +
                        "SIMPL: " + DebugTrackStore.simplifiedCount + "\n" +
                        "SMS LEN: " + DebugTrackStore.smsLength;

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
        handler.removeCallbacksAndMessages(null);

        // 🔥 CLEANUP
        if (rawPolyline != null) rawPolyline.remove();
        if (filteredPolyline != null) filteredPolyline.remove();
        if (simplifiedPolyline != null) simplifiedPolyline.remove();
    }
}


