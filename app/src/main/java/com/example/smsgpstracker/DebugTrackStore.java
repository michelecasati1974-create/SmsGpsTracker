package com.example.smsgpstracker;

import com.google.android.gms.maps.model.LatLng;
import java.util.List;

public class DebugTrackStore {

    public static List<LatLng> raw;
    public static List<LatLng> filtered;
    public static List<LatLng> simplified;

    // 🔥 NUOVI
    public static int rawCount;
    public static int filteredCount;
    public static int simplifiedCount;
    public static int sentCount;

    public static String lastSms;
    public static int smsLength;

    public static void clear() {
        raw = null;
        filtered = null;
        simplified = null;
        lastSms = null;
    }
}


