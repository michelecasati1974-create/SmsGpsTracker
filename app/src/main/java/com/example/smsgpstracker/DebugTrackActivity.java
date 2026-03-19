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



public class DebugTrackActivity extends AppCompatActivity
        implements OnMapReadyCallback {
    public static boolean isOpen = false;
    private GoogleMap map;
    private TextView statsView;
    private Handler handler = new Handler();



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_debug_track);

        isOpen = true;

        statsView = findViewById(R.id.stats);

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

                updateStats();

                if (map != null) {
                    refreshMap();
                }

                handler.postDelayed(this, 1000);
            }
        }, 1000);
    }

    private void updateStats() {

        String text =
                "RAW: " + DebugTrackStore.rawCount + "\n" +
                        "FILTER: " + DebugTrackStore.filteredCount + "\n" +
                        "SIMPL: " + DebugTrackStore.simplifiedCount + "\n" +
                        "SMS LEN: " + DebugTrackStore.smsLength + "\n" +
                        "SMS: " + DebugTrackStore.lastSms;

        statsView.setText(text);
    }

    private void refreshMap() {
        map.clear();
        loadTrack();
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {

        map = googleMap;

        loadTrack();
    }

    private void loadTrack() {

        if (map == null) return;

        // ======================
        // RAW TRACK (rosso)
        // ======================

        if (DebugTrackStore.raw != null &&
                !DebugTrackStore.raw.isEmpty()) {

            map.addPolyline(
                    new PolylineOptions()
                            .addAll(DebugTrackStore.raw)
                            .color(Color.RED)
                            .width(4)
            );

            // marker start
            LatLng start = DebugTrackStore.raw.get(0);

            map.addMarker(
                    new MarkerOptions()
                            .position(start)
                            .title("START")
            );

            // marker end
            LatLng end =
                    DebugTrackStore.raw.get(
                            DebugTrackStore.raw.size() - 1
                    );

            map.addMarker(
                    new MarkerOptions()
                            .position(end)
                            .title("END")
            );
        }

        // ======================
        // FILTERED TRACK (giallo)
        // ======================

        if (DebugTrackStore.filtered != null &&
                !DebugTrackStore.filtered.isEmpty()) {

            map.addPolyline(
                    new PolylineOptions()
                            .addAll(DebugTrackStore.filtered)
                            .color(Color.YELLOW)
                            .width(6)
            );
        }

        // ======================
        // SIMPLIFIED TRACK (verde)
        // ======================

        if (DebugTrackStore.simplified != null &&
                !DebugTrackStore.simplified.isEmpty()) {

            map.addPolyline(
                    new PolylineOptions()
                            .addAll(DebugTrackStore.simplified)
                            .color(Color.GREEN)
                            .width(8)
            );
        }

        moveCamera();
    }

    private void moveCamera() {

        if (map == null) return;

        if (DebugTrackStore.raw == null ||
                DebugTrackStore.raw.isEmpty()) {
            return;
        }

        LatLng first = DebugTrackStore.raw.get(0);

        map.moveCamera(
                CameraUpdateFactory.newLatLngZoom(first, 17f)
        );
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isOpen = false;
        // pulizia memoria debug
        DebugTrackStore.clear();
    }
}


