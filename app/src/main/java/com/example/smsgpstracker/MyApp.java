package com.example.smsgpstracker;

import android.app.Application;
import android.util.Log;

public class MyApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        Log.e("SERVICE_LIFECYCLE", "🔥 APP PROCESS CREATED");

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            Log.e("CRASH_FATAL", "UNCAUGHT EXCEPTION", throwable);
        });
    }
}