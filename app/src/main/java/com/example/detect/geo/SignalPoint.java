package com.example.detect.geo;

public class SignalPoint {
    public final long id;
    public final double lat;
    public final double lon;
    public SignalPoint(long id, double lat, double lon) {
        this.id = id; this.lat = lat; this.lon = lon;
    }
}