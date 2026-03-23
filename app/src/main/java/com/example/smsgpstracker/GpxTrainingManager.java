package com.example.smsgpstracker;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;

import java.io.File;
import java.util.List;

public class GpxTrainingManager {

    public static void runTraining(Context ctx) {

        File folder = new File(
                ctx.getExternalFilesDir(null),
                "gpx"
        );

        Log.d("GPX", "Folder path: " + folder.getAbsolutePath());
        Log.d("GPX", "Folder exists: " + folder.exists());

        // 🔍 DEBUG: lista TUTTI i file in Download
        File[] files = folder.listFiles();

        if (files == null) {
            Log.e("GPX", "FILES = NULL (permessi o accesso negato)");
            return;
        }

        Log.d("GPX", "Files found: " + files.length);

        for (File f : files) {
            Log.d("GPX", "File: " + f.getName());
        }

        if (files.length == 0) return;

        AdaptiveConfig globalBest = null;

        for (File f : files) {

            String name = f.getName().toLowerCase();

            Log.d("GPX", "Checking file: " + name);

            if (!name.endsWith(".gpx") && !name.endsWith(".gps")) {
                Log.d("GPX", "Skipped (extension): " + name);
                continue;
            }

            Log.d("GPX", "Processing file: " + name);

            List<LatLng> track = GpxParser.parse(f);

            Log.d("GPX", "Parsed points: " + (track != null ? track.size() : -1));

            if (track == null || track.size() < 10) {
                Log.e("GPX", "Track too small, skipping");
                continue;
            }

            Log.d("GPX", "Calling optimizer...");

            AdaptiveConfig best = ParameterOptimizer.optimize(track);

            Log.d("GPX", "Optimizer returned: " + (best != null));

            if (best != null) {
                globalBest = best;
            }
        }

        if (globalBest == null) {
            Log.d("TEST_SAVE", "No valid GPX processed");
            return;
        }

        // 💾 SALVATAGGIO
        SharedPreferences prefs =
                ctx.getSharedPreferences("SmsGpsTrackerPrefs", Context.MODE_PRIVATE);

        prefs.edit()
                .putFloat("multi_min_distance", globalBest.distance)
                .putFloat("multi_angle_threshold", globalBest.angle)
                .putFloat("multi_simplify_tolerance", globalBest.epsilon)
                .putLong("multi_send_interval", globalBest.intervalMs)
                .apply();

        Log.d("TEST_SAVE", "Training finished!");
        Log.d("TEST_SAVE", "interval=" + globalBest.intervalMs);
        Log.d("TEST_SAVE", "distance=" + globalBest.distance);
        Log.d("TEST_SAVE", "angle=" + globalBest.angle);
        Log.d("TEST_SAVE", "epsilon=" + globalBest.epsilon);
    }
}