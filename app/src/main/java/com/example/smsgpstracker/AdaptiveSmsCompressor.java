package com.example.smsgpstracker;

import android.util.Log;
import com.google.android.gms.maps.model.LatLng;
import java.util.ArrayList;
import java.util.List;
import com.example.smsgpstracker.tx.TrackSimplifier;
import com.example.smsgpstracker.tx.PolylineCodec;

public class AdaptiveSmsCompressor {

    private static final int SMS_MAX = 150;
    public static int lastEncodedLength = 0;
    public static int lastPoints = 0;
    public static int lastIterations = 0;

    public static CompressionResult compressToSms(
            List<LatLng> raw,
            double baseEpsilon,
            double distance,
            double angle
    ) {

        Log.d("ADAPT", "START compression");
        Log.d("ADAPT", "RAW points: " + raw.size());

        // STEP 1: filtro base
        List<LatLng> filtered =
                BasicFilter.apply(raw, (float) distance, (float) angle);

        Log.d("ADAPT", "FILTERED: " + filtered.size());

        double epsilon = baseEpsilon;
        List<LatLng> simplified;
        String encoded;

        int iteration = 0;

        while (true) {

            iteration++;

            simplified =
                    TrackSimplifier.simplify(filtered, epsilon);

            encoded = encode(simplified);

            Log.d("ADAPT", "Iter " + iteration +
                    " | eps=" + epsilon +
                    " | pts=" + simplified.size() +
                    " | len=" + encoded.length());

            // 🎯 TARGET CENTRATO
            if (encoded.length() <= SMS_MAX) {

                lastEncodedLength = encoded.length();
                lastPoints = simplified.size();
                lastIterations = iteration;

                break;
            }

            // aumenta compressione
            epsilon *= 1.2;

            // sicurezza anti-loop
            if (iteration > 20) {
                Log.e("ADAPT", "BREAK SAFETY");

                lastEncodedLength = encoded.length();
                lastPoints = simplified.size();
                lastIterations = iteration;

                break;
            }
        }

        Log.d("ADAPT", "FINAL len=" + encoded.length());

        CompressionResult res = new CompressionResult();

        res.encoded = encoded;
        res.finalLength = encoded.length();
        res.smsCount = 1; // per ora 1 SMS
        res.usedEpsilon = epsilon;
        res.usedDistance = distance;

        return res;
    }

    // --------------------------------------------------

    private static String encode(List<LatLng> pts) {

        List<kotlin.Pair<Double, Double>> list = new ArrayList<>();

        for (LatLng p : pts) {
            list.add(new kotlin.Pair<>(p.latitude, p.longitude));
        }

        return PolylineCodec.INSTANCE.encode(list);
    }


}