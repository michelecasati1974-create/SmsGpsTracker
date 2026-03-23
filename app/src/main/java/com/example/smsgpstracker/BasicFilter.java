package com.example.smsgpstracker;

import com.google.android.gms.maps.model.LatLng;
import java.util.ArrayList;
import java.util.List;

public class BasicFilter {

    public static List<LatLng> apply(
            List<LatLng> input,
            float minDistance,
            float angleThreshold
    ) {

        List<LatLng> out = new ArrayList<>();

        if (input == null || input.size() < 2)
            return input;

        LatLng lastKept = input.get(0);
        out.add(lastKept);

        for (int i = 1; i < input.size() - 1; i++) {

            LatLng current = input.get(i);

            double dist = distance(lastKept, current);

            if (dist < minDistance)
                continue;

            // angolo
            LatLng next = input.get(i + 1);

            double angle = angle(lastKept, current, next);

            if (Math.abs(angle) < angleThreshold)
                continue;

            out.add(current);
            lastKept = current;
        }

        out.add(input.get(input.size() - 1));

        return out;
    }

    private static double distance(LatLng a, LatLng b) {
        double dx = a.latitude - b.latitude;
        double dy = a.longitude - b.longitude;
        return Math.sqrt(dx * dx + dy * dy) * 111000; // approx metri
    }

    private static double angle(LatLng a, LatLng b, LatLng c) {

        double abx = b.latitude - a.latitude;
        double aby = b.longitude - a.longitude;

        double bcx = c.latitude - b.latitude;
        double bcy = c.longitude - b.longitude;

        double dot = abx * bcx + aby * bcy;
        double mag1 = Math.sqrt(abx * abx + aby * aby);
        double mag2 = Math.sqrt(bcx * bcx + bcy * bcy);

        if (mag1 == 0 || mag2 == 0) return 0;

        double cos = dot / (mag1 * mag2);

        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, cos))));
    }
}