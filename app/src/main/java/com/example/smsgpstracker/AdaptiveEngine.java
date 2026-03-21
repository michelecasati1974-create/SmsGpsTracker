package com.example.smsgpstracker;

import android.util.Log;

public class AdaptiveEngine {

    private static final double TARGET_RATIO = 0.01;

    public static AdaptiveConfig adjust(
            AdaptiveConfig current,
            AdaptiveSession lastSession
    ) {

        // 🔥 PROTEZIONE
        if (current == null || lastSession == null) {
            return current; // meglio NON tornare null
        }

        AdaptiveConfig next = current.copy();

        double ratio = lastSession.compressionRatio;

        Log.d("ADAPTIVE", "ratio=" + ratio);

        // =========================
        // 🔽 Troppi SMS → comprimi
        // =========================
        if (ratio > TARGET_RATIO) {

            next.epsilon *= 1.2f;
            next.distance += 2f;
            next.angle += 2f;

            Log.d("ADAPTIVE", "Increase compression");
        }

        // =========================
        // 🔼 Troppo pochi SMS → qualità
        // =========================
        else if (ratio < TARGET_RATIO * 0.5) {

            next.epsilon *= 0.85f;
            next.distance -= 1f;
            next.angle -= 1f;

            Log.d("ADAPTIVE", "Improve precision");
        }

        // 🔒 LIMITI SICUREZZA
        next.distance = clamp(next.distance, 5, 50);
        next.angle = clamp(next.angle, 3, 30);
        next.epsilon = clamp(next.epsilon, 0.00001f, 0.001f);

        return next;
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}

