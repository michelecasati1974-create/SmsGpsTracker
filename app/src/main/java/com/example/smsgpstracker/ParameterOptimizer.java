package com.example.smsgpstracker;

import com.google.android.gms.maps.model.LatLng;
import java.util.List;

public class ParameterOptimizer {

    public static AdaptiveConfig optimize(List<LatLng> track) {

        AdaptiveConfig best = null;
        double bestScore = Double.MAX_VALUE;

        for (float d = 5; d <= 20; d += 3) {
            for (float a = 3; a <= 15; a += 2) {
                for (float e = 0.00001f; e <= 0.0002f; e *= 1.5f) {

                    AdaptiveConfig c =
                            new AdaptiveConfig(d, a, e, 600000);

                    SimulationResult r =
                            OfflineSimulator.run(track, c);

                    double score =
                            (r.smsCount * 1000)
                                    + Math.abs(r.avgLength - 145) * 5;

                    if (score < bestScore) {
                        bestScore = score;
                        best = c;
                    }
                }
            }
        }

        return best;
    }
}