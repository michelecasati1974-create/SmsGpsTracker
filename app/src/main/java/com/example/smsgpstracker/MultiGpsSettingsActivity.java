package com.example.smsgpstracker;

import android.app.*;
import android.os.*;
import com.google.android.gms.location.*;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.util.Log;


public class MultiGpsSettingsActivity extends AppCompatActivity {

    private SharedPreferences prefs;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d("TEST", "APP AVVIATA");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_multi_gps_settings);

        prefs = getSharedPreferences("SmsGpsTrackerPrefs", MODE_PRIVATE);

        setupFields();
        Log.d("TEST_UI", "onCreate chiamato");
    }

    private void setupFields() {

        EditText edtInterval = findViewById(R.id.edtInterval);
        EditText edtDistance = findViewById(R.id.edtDistance);
        EditText edtAngle = findViewById(R.id.edtAngle);
        EditText edtEpsilon = findViewById(R.id.edtEpsilon);
        EditText edtMinPoints = findViewById(R.id.edtMinPoints);
        EditText edtKeep = findViewById(R.id.edtKeep);

        // DEFAULT TREKKING
        edtInterval.setText(String.valueOf(
                prefs.getLong("multi_send_interval", 240000)));

        edtDistance.setText(String.valueOf(
                prefs.getFloat("multi_min_distance", 8)));

        edtAngle.setText(String.valueOf(
                prefs.getFloat("multi_angle_threshold", 5)));

        edtEpsilon.setText(String.valueOf(
                prefs.getFloat("multi_simplify_tolerance", 0.00005f)));

        edtMinPoints.setText(String.valueOf(
                prefs.getInt("multi_min_points", 5)));

        edtKeep.setText(String.valueOf(
                prefs.getInt("multi_keep_points", 2)));

        Button btnSave = findViewById(R.id.btnSave);

        btnSave.setOnClickListener(v -> {

            prefs.edit()
                    .putLong("multi_send_interval",
                            Long.parseLong(edtInterval.getText().toString()))

                    .putFloat("multi_min_distance",
                            Float.parseFloat(edtDistance.getText().toString()))

                    .putFloat("multi_angle_threshold",
                            Float.parseFloat(edtAngle.getText().toString()))

                    .putFloat("multi_simplify_tolerance",
                            Float.parseFloat(edtEpsilon.getText().toString()))

                    .putInt("multi_min_points",
                            Integer.parseInt(edtMinPoints.getText().toString()))

                    .putInt("multi_keep_points",
                            Integer.parseInt(edtKeep.getText().toString()))

                    .apply();

            finish();
            Log.d("TEST_UI", "interval=" + prefs.getLong("multi_send_interval", -1));
            Log.d("TEST_UI", "distance=" + prefs.getFloat("multi_min_distance", -1));
            Log.d("TEST_UI", "angle=" + prefs.getFloat("multi_angle_threshold", -1));
            Log.d("TEST_UI", "epsilon=" + prefs.getFloat("multi_simplify_tolerance", -1));
            Log.d("TEST_UI", "minPoints=" + prefs.getInt("multi_min_points", -1));
            Log.d("TEST_UI", "keep=" + prefs.getInt("multi_keep_points", -1));
        });
        Log.d("TEST_UI", "onCreate chiamato");
    }
}