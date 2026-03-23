package com.example.smsgpstracker;

import com.google.android.gms.maps.model.LatLng;
import java.util.*;
import android.util.Log;
import com.example.smsgpstracker.tx.TrackSimplifier;
import com.example.smsgpstracker.tx.PolylineCodec;

public class OfflineSimulator {

    private static final int SMS_MAX = 150;

    public static SimulationResult run(
            List<LatLng> raw,
            AdaptiveConfig config
    ) {

        // 🔍 DEBUG INIZIO
        Log.d("SIM", "====================");
        Log.d("SIM", "START SIMULATION");
        Log.d("SIM", "RAW points: " + (raw != null ? raw.size() : -1));
        Log.d("SIM", "CONFIG -> d=" + config.distance +
                " a=" + config.angle +
                " e=" + config.epsilon);

        // 🧠 FILTER
        List<LatLng> filtered =
                BasicFilter.apply(raw, config.distance, config.angle);

        Log.d("SIM", "FILTERED points: " + filtered.size());

        // 🧠 SIMPLIFY
        List<LatLng> simplified =
                TrackSimplifier.simplify(filtered, config.epsilon);

        Log.d("SIM", "SIMPLIFIED points: " + simplified.size());

        int smsCount = 0;
        int totalLen = 0;

        List<LatLng> working = new ArrayList<>(simplified);

        Log.d("SIM", "START SMS BUILD, working points: " + working.size());

        // 🔁 BUILD SMS
        while (!working.isEmpty()) {

            Log.d("SIM", "Remaining points: " + working.size());

            List<LatLng> chunk = new ArrayList<>();

            for (LatLng p : working) {

                chunk.add(p);

                String encoded = encode(chunk);

                if (encoded.length() > SMS_MAX) {
                    chunk.remove(chunk.size() - 1);
                    break;
                }
            }

            // ⚠️ PROTEZIONE LOOP
            if (chunk.isEmpty()) {
                Log.e("SIM", "Chunk EMPTY → breaking loop!");
                break;
            }

            String encoded = encode(chunk);

            Log.d("SIM", "SMS len: " + encoded.length()
                    + " | points in chunk: " + chunk.size());

            smsCount++;
            totalLen += encoded.length();

            // ✂️ RIMUOVE PUNTI PROCESSATI
            working = working.subList(
                    chunk.size(),
                    working.size()
            );
        }

        SimulationResult r = new SimulationResult();
        r.smsCount = smsCount;
        r.totalLength = totalLen;
        r.avgLength = smsCount > 0 ? totalLen / smsCount : 0;

        // 🔍 DEBUG FINALE
        Log.d("SIM", "FINAL SMS count: " + smsCount);
        Log.d("SIM", "TOTAL length: " + totalLen);
        Log.d("SIM", "AVG length: " + r.avgLength);
        Log.d("SIM", "END SIMULATION");
        Log.d("SIM", "====================");

        return r;
    }

    private static String encode(List<LatLng> pts) {

        List<kotlin.Pair<Double, Double>> list = new ArrayList<>();

        for (LatLng p : pts) {
            list.add(new kotlin.Pair<>(p.latitude, p.longitude));
        }

        return PolylineCodec.INSTANCE.encode(list);
    }
}