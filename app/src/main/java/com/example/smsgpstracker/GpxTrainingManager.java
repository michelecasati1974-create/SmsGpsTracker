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
                Environment.getExternalStorageDirectory(),
                "Documents/SmsGpsTracker/gpx"
        );
        Log.d("TEST_SAVE", "GPX Training started, folder: " + folder.getAbsolutePath());

        if (!folder.exists()) return;

        File[] files = folder.listFiles();

        if (files == null || files.length == 0) return;

        AdaptiveConfig globalBest = null;

        for (File f : files) {

            if (!f.getName().endsWith(".gpx")) continue;

            List<LatLng> track = GpxParser.parse(f);

            AdaptiveConfig best =
                    ParameterOptimizer.optimize(track);

            globalBest = best; // semplificato (ultimo vince)
        }

        if (globalBest != null) {
            Log.d("TEST_SAVE", "No GPX tracks processed or no files found.");
        } else {
            Log.d("TEST_SAVE", "Training finished, best config applied.");

            SharedPreferences prefs =
                    ctx.getSharedPreferences("SmsGpsTrackerPrefs", Context.MODE_PRIVATE);

            prefs.edit()
                    .putFloat("multi_min_distance", globalBest.distance)
                    .putFloat("multi_angle_threshold", globalBest.angle)
                    .putFloat("multi_simplify_tolerance", globalBest.epsilon)
                    .putLong("multi_send_interval", globalBest.intervalMs)
                    .apply();
            Log.d("TEST_SAVE", "interval=" + globalBest.intervalMs);
            Log.d("TEST_SAVE", "distance=" + globalBest.distance);
            Log.d("TEST_SAVE", "angle=" + globalBest.angle);
            Log.d("TEST_SAVE", "epsilon=" + globalBest.epsilon);
        }


    }
}
