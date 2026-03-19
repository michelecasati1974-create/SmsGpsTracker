package com.example.smsgpstracker;

import android.app.*;
import android.os.*;
import com.google.android.gms.location.*;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;


public class MultiGpsSettingsActivity extends AppCompatActivity {

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_multi_gps_settings);

        prefs = getSharedPreferences("SmsGpsTrackerPrefs", MODE_PRIVATE);

        setupFields();
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
                prefs.getInt("multi_max_points_sms", 5)));

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

                    .putInt("multi_max_points_sms",
                            Integer.parseInt(edtMinPoints.getText().toString()))

                    .putInt("multi_keep_points",
                            Integer.parseInt(edtKeep.getText().toString()))

                    .apply();

            finish();
        });
    }
}