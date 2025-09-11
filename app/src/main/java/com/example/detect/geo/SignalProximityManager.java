package com.example.detect.geo;

import android.content.Context;
import android.location.Location;
import android.os.Build;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;

import com.google.android.gms.location.*;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 背景接收 GPS，找前方 ±45°、距離 ≤ 50m 的紅綠燈點 */
public class SignalProximityManager {

    public interface Listener {
        void onApproach(SignalPoint p, float distMeters);
    }

    private final Context app;
    private final FusedLocationProviderClient fused;
    private final SignalRepository repo;
    private final Listener listener;
    private final ExecutorService bg = Executors.newSingleThreadExecutor();

    // 參數可調
    private float alertDistanceM = 50f;
    private float searchRadiusM  = 120f;
    private float frontAngleDeg  = 35f;
    private long cooldownMs      = 8000L;

    private long lastAlertTs = 0L;

    private Location lastLoc = null; // 用來估 heading

    public SignalProximityManager(Context ctx, SignalRepository repo, Listener listener) {
        this.app = ctx.getApplicationContext();
        this.repo = repo;
        this.listener = listener;
        this.fused = LocationServices.getFusedLocationProviderClient(app);
    }

    public void start() {
        LocationRequest req = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 1500 /*ms*/)
                .setMinUpdateIntervalMillis(800)
                .setMinUpdateDistanceMeters(3f)
                .build();
        fused.requestLocationUpdates(req, callback, Looper.getMainLooper());
    }

    public void stop() {
        fused.removeLocationUpdates(callback);
        bg.shutdownNow();
    }

    private final LocationCallback callback = new LocationCallback() {
        @Override public void onLocationResult(LocationResult result) {
            final Location cur = result.getLastLocation();
            if (cur == null) return;

            // 低速不提醒，避免等紅燈重覆震動
            if (cur.getSpeed() < 2.0f) { // m/s ≈ 7.2 km/h
                lastLoc = cur;
                return;
            }

            final float bearing = estimateBearing(lastLoc, cur); // 0~360
            final Location prev = lastLoc;
            lastLoc = cur;

            bg.execute(() -> checkAndAlert(prev, cur, bearing));
        }
    };

    private void checkAndAlert(Location prev, Location cur, float headingDeg) {
        List<SignalPoint> all = repo.getAll();
        SignalPoint best = null;
        float bestD = Float.MAX_VALUE;

        for (SignalPoint p : all) {
            float[] res = new float[1];
            Location.distanceBetween(cur.getLatitude(), cur.getLongitude(),
                    p.lat, p.lon, res);
            float d = res[0];
            if (d > searchRadiusM) continue;

            // 前方判定：計算當前→目標的方位角
            float bearingTo = bearingTo(cur, p.lat, p.lon);
            float diff = angleDiffDeg(headingDeg, bearingTo);
            if (diff > frontAngleDeg) continue;

            if (d < bestD) { bestD = d; best = p; }
        }

        if (best != null && bestD <= alertDistanceM) {
            long now = System.currentTimeMillis();
            if (now - lastAlertTs > cooldownMs) {
                lastAlertTs = now;
                vibrate(300);
                if (listener != null) listener.onApproach(best, bestD);
            }
        }
    }

    /** 用兩點估算行進方向，或用裝置 bearing（若可用） */
    private float estimateBearing(Location last, Location cur) {
        if (cur.hasBearing() && cur.getBearingAccuracyDegrees() <= 30) {
            float b = cur.getBearing();
            return (b < 0) ? b + 360f : b;
        }
        if (last == null) return 0f;
        return bearingTo(last, cur.getLatitude(), cur.getLongitude());
    }

    private float bearingTo(Location from, double lat, double lon) {
        Location tmp = new Location("tmp"); tmp.setLatitude(lat); tmp.setLongitude(lon);
        float b = from.bearingTo(tmp); // -180..+180
        if (b < 0) b += 360f;
        return b;
    }

    private float angleDiffDeg(float a, float b) {
        float d = Math.abs(a - b) % 360f;
        return (d > 180f) ? 360f - d : d;
    }

    private void vibrate(long ms) {
        try {
            Vibrator v = (Vibrator) app.getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null) return;
            if (Build.VERSION.SDK_INT >= 26)
                v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            else v.vibrate(ms);
        } catch (Exception ignore) {}
    }
}
