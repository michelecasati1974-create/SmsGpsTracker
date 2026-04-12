package com.example.smsgpstracker;
import com.google.android.gms.maps.model.LatLng;
import java.util.List;

public class CompressionResult {

    public String encoded;

    public int smsCount;

    public int finalLength;

    public double usedEpsilon;

    public double usedDistance;

    public List<LatLng> simplifiedPoints;
}
