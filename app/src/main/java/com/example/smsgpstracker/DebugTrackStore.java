package com.example.smsgpstracker;

import com.google.android.gms.maps.model.LatLng;
import java.util.List;

public class DebugTrackStore {

    public static List<LatLng> raw;
    public static List<LatLng> filtered;
    public static List<LatLng> simplified;

    public static void clear() {
        raw = null;
        filtered = null;
        simplified = null;
    }
}


