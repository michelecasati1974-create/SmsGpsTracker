package com.example.smsgpstracker;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

public class AdaptiveStore {

    private static final String PREF = "ADAPTIVE_STORE";
    private static final String KEY = "sessions";

    public static void saveSession(Context ctx, AdaptiveSession s) {

        try {
            SharedPreferences prefs =
                    ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);

            String str = prefs.getString(KEY, "[]");
            JSONArray arr = new JSONArray(str);

            JSONObject o = new JSONObject();

            o.put("gps", s.gpsPoints);
            o.put("sms", s.smsSent);
            o.put("dist", s.distanceKm);
            o.put("acc", s.avgAccuracy);
            o.put("dur", s.durationSec);

            o.put("d", s.distanceParam);
            o.put("a", s.angleParam);
            o.put("e", s.epsilonParam);
            o.put("i", s.intervalParam);

            o.put("ratio", s.compressionRatio);

            arr.put(o);

            prefs.edit().putString(KEY, arr.toString()).apply();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}