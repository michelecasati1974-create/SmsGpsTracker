package com.example.smsgpstracker;

import android.util.Log;
import com.google.android.gms.maps.model.LatLng;
import java.util.ArrayList;
import java.util.List;
import com.example.smsgpstracker.tx.TrackSimplifier;
import com.example.smsgpstracker.tx.PolylineCodec;

public class AdaptiveSmsCompressor {


    public static int lastEncodedLength = 0;
    public static int lastPoints = 0;
    public static int lastIterations = 0;

    private static int computeSmsLength(String encoded) {

        String payload = "T|" + encoded;

        String sms = payload + "|" + SmsCrc.INSTANCE.crc8(payload);

        return sms.length();
    }

    public static CompressionResult compressToSms(

            List<LatLng> raw,
            double baseEpsilon,
            double distance,
            double angle,
            int dynamicTarget
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

        // ================================
        // 🧠 STEP 4.1 — BEST TRACKING
        // ================================
        String bestEncoded = null;
        int bestLen = Integer.MAX_VALUE;
        List<LatLng> bestSimplified = null;

        while (true) {

            iteration++;

            simplified = TrackSimplifier.simplify(filtered, epsilon);
            encoded = encode(simplified);

            int smsLength = computeSmsLength(encoded);

            Log.d("ADAPT", "Iter " + iteration +
                    " | eps=" + epsilon +
                    " | pts=" + simplified.size() +
                    " | encLen=" + encoded.length() +
                    " | smsLen=" + smsLength +
                    " | target=" + dynamicTarget);

            // ================================
            // 🎯 STEP 4.2 — SALVA BEST FIT
            // ================================
            if (smsLength <= dynamicTarget && smsLength < bestLen) {
                bestLen = smsLength;
                bestEncoded = encoded;
                bestSimplified = simplified;
            }

            // ================================
            // 🚨 STOP quando superi il target
            // ================================
            if (smsLength > dynamicTarget && bestEncoded != null) {
                break;
            }

            // 🔁 aumenta compressione
            epsilon *= 1.2;

            // 🛑 sicurezza anti-loop
            if (iteration > 20) {
                Log.e("ADAPT", "BREAK SAFETY");
                break;
            }
        }

        // ================================
        // ✅ STEP 4.3 — USA BEST RISULTATO
        // ================================
        if (bestEncoded != null) {
            encoded = bestEncoded;
            simplified = bestSimplified;
        }

        // ================================
        // 📊 STEP 4.4 — METRICHE FINALI
        // ================================
        int finalSmsLen = computeSmsLength(encoded);

        lastEncodedLength = finalSmsLen;
        lastPoints = simplified.size();
        lastIterations = iteration;

        Log.d("ADAPT", "FINAL SMS len=" + finalSmsLen);

        CompressionResult res = new CompressionResult();

        res.encoded = encoded;
        res.finalLength = finalSmsLen;
        res.smsCount = 1;
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