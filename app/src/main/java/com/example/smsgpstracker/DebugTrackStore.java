package com.example.smsgpstracker;

import com.google.android.gms.maps.model.LatLng;
import java.util.List;
import java.util.ArrayList;

public class DebugTrackStore {


    public static List<Integer> smsHistory = new ArrayList<>();
    public static List<LatLng> raw = new ArrayList<>();
    public static List<LatLng> filtered = new ArrayList<>();
    public static List<LatLng> simplified = new ArrayList<>();

    // 🔥 NUOVI
    public static int rawCount;
    public static int filteredCount;
    public static int simplifiedCount;
    public static int sentCount;

    public static String lastSms;
    public static int smsLength;

    public static void clear() {
        raw.clear();
        filtered.clear();
        simplified.clear();
        rawHistory.clear();
        filteredHistory.clear();
        simplifiedHistory.clear();
        timeHistory.clear();
        smsHistory.clear();

        lastSms = null;

        rawCount = 0;
        filteredCount = 0;
        simplifiedCount = 0;
    }
    public static void appendRaw(List<LatLng> newPoints) {
        if (newPoints != null) raw.addAll(newPoints);
    }

    public static void appendFiltered(List<LatLng> newPoints) {
        if (newPoints != null) filtered.addAll(newPoints);
    }

    public static void appendSimplified(List<LatLng> newPoints) {
        if (newPoints != null) simplified.addAll(newPoints);
    }
    public static List<Integer> rawHistory = new ArrayList<>();
    public static List<Integer> filteredHistory = new ArrayList<>();
    public static List<Integer> simplifiedHistory = new ArrayList<>();
    public static List<Long> timeHistory = new ArrayList<>();

    public static void reset() {

        raw.clear();
        filtered.clear();
        simplified.clear();

        rawHistory.clear();
        filteredHistory.clear();
        simplifiedHistory.clear();
        smsHistory.clear();

        rawCount = 0;
        filteredCount = 0;
        simplifiedCount = 0;
        smsLength = 0;
        lastSms = null;
    }
}


