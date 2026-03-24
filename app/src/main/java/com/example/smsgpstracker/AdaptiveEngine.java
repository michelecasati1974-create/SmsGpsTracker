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

        if (ratio > TARGET_RATIO) {

            // 🔽 Troppi SMS → comprimi + rallenta invio
            next.epsilon *= 1.2f;
            next.distance += 2f;
            next.angle += 2f;

            next.intervalMs *= 1.2f; // 🔥 PIÙ TEMPO TRA SMS

            Log.d("ADAPTIVE", "Increase compression + interval");
        }
        else if (ratio < TARGET_RATIO * 0.5) {

            // 🔼 Troppo pochi SMS → più precisione + più frequenza
            next.epsilon *= 0.85f;
            next.distance -= 1f;
            next.angle -= 1f;

            next.intervalMs *= 0.85f; // 🔥 PIÙ FREQUENTE

            Log.d("ADAPTIVE", "Improve precision + interval");
        }

        next.intervalMs = clampLong(next.intervalMs,
                60_000,     // 1 min
                600_000);   // 10 min

        // 🔒 LIMITI SICUREZZA
        next.distance = clamp(next.distance, 5, 50);
        next.angle = clamp(next.angle, 3, 30);
        next.epsilon = clamp(next.epsilon, 0.00001f, 0.001f);

        return next;


    }

    private static long clampLong(long v, long min, long max) {
        return Math.max(min, Math.min(max, v));
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}

