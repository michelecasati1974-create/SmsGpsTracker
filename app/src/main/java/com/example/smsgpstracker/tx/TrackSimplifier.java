package com.example.smsgpstracker.tx;

import com.google.android.gms.maps.model.LatLng;
import java.util.ArrayList;
import java.util.List;


public class TrackSimplifier {

    public static List<LatLng> simplify(List<LatLng> points, double epsilon) {

        if (points.size() < 3) return points;

        int index = -1;
        double maxDist = 0;

        LatLng first = points.get(0);
        LatLng last = points.get(points.size() - 1);

        for (int i = 1; i < points.size() - 1; i++) {

            double dist = perpendicularDistance(points.get(i), first, last);

            if (dist > maxDist) {
                index = i;
                maxDist = dist;
            }
        }

        if (maxDist > epsilon) {

            List<LatLng> left =
                    simplify(points.subList(0, index + 1), epsilon);

            List<LatLng> right =
                    simplify(points.subList(index, points.size()), epsilon);

            List<LatLng> result = new ArrayList<>(left);
            result.remove(result.size() - 1);
            result.addAll(right);

            return result;

        } else {

            List<LatLng> result = new ArrayList<>();
            result.add(first);
            result.add(last);

            return result;
        }
    }

    private static double perpendicularDistance(
            LatLng p, LatLng start, LatLng end) {

        double dx = end.longitude - start.longitude;
        double dy = end.latitude - start.latitude;

        if (dx == 0 && dy == 0) {

            dx = p.longitude - start.longitude;
            dy = p.latitude - start.latitude;

            return Math.sqrt(dx * dx + dy * dy);
        }

        double t =
                ((p.longitude - start.longitude) * dx +
                        (p.latitude - start.latitude) * dy) /
                        (dx * dx + dy * dy);

        double projX = start.longitude + t * dx;
        double projY = start.latitude + t * dy;

        double dX = p.longitude - projX;
        double dY = p.latitude - projY;

        return Math.sqrt(dX * dX + dY * dY);
    }
}
