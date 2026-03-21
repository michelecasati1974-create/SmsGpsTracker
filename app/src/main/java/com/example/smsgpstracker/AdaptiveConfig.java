package com.example.smsgpstracker;

public class AdaptiveConfig {

    public float distance;      // metri
    public float angle;         // gradi
    public float epsilon;       // semplificazione
    public long intervalMs;     // invio SMS


    @Override
    public String toString() {
        return "interval=" + intervalMs +
                ", distance=" + distance +
                ", angle=" + angle +
                ", epsilon=" + epsilon;
    }
    public AdaptiveConfig(float d, float a, float e, long i) {
        this.distance = d;
        this.angle = a;
        this.epsilon = e;
        this.intervalMs = i;
    }

    public AdaptiveConfig copy() {
        return new AdaptiveConfig(distance, angle, epsilon, intervalMs);
    }
}