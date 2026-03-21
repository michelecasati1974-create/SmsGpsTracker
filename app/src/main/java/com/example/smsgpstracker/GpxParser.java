package com.example.smsgpstracker;

import com.google.android.gms.maps.model.LatLng;
import org.w3c.dom.*;
import java.io.File;
import java.util.*;
import javax.xml.parsers.*;

public class GpxParser {

    public static List<LatLng> parse(File file) {

        List<LatLng> points = new ArrayList<>();

        try {
            DocumentBuilder builder =
                    DocumentBuilderFactory.newInstance().newDocumentBuilder();

            Document doc = builder.parse(file);

            NodeList nodes = doc.getElementsByTagName("trkpt");

            for (int i = 0; i < nodes.getLength(); i++) {

                Element el = (Element) nodes.item(i);

                double lat = Double.parseDouble(el.getAttribute("lat"));
                double lon = Double.parseDouble(el.getAttribute("lon"));

                points.add(new LatLng(lat, lon));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return points;
    }
}
