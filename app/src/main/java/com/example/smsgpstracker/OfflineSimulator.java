package com.example.smsgpstracker;

import com.google.android.gms.maps.model.LatLng;
import java.util.*;
import com.example.smsgpstracker.tx.TrackSimplifier;
import com.example.smsgpstracker.tx.PolylineCodec;



public class OfflineSimulator {

    private static final int SMS_MAX = 150;

    public static SimulationResult run(
            List<LatLng> raw,
            AdaptiveConfig config
    ) {

        List<LatLng> simplified =
                TrackSimplifier.simplify(raw, config.epsilon);

        int smsCount = 0;
        int totalLen = 0;

        List<LatLng> working = new ArrayList<>(simplified);

        while (!working.isEmpty()) {

            List<LatLng> chunk = new ArrayList<>();

            for (LatLng p : working) {

                chunk.add(p);

                String encoded = encode(chunk);

                if (encoded.length() > SMS_MAX) {
                    chunk.remove(chunk.size() - 1);
                    break;
                }
            }

            String encoded = encode(chunk);

            smsCount++;
            totalLen += encoded.length();

            working = working.subList(
                    chunk.size(),
                    working.size()
            );
        }

        SimulationResult r = new SimulationResult();
        r.smsCount = smsCount;
        r.totalLength = totalLen;
        r.avgLength = smsCount > 0 ? totalLen / smsCount : 0;

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