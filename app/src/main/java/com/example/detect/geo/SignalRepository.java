package com.example.detect.geo;

import android.content.Context;
import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** 從 assets export.geojson 讀出所有紅綠燈點 */
public class SignalRepository {
    private final Context app;
    private final List<SignalPoint> points = new ArrayList<>();

    public SignalRepository(Context ctx) { this.app = ctx.getApplicationContext(); }

    public void loadFromAssets(String fileName) {
        points.clear();
        try (InputStream is = app.getAssets().open(fileName);
             InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
             JsonReader jr = new JsonReader(isr)) {

            JsonObject root = JsonParser.parseReader(jr).getAsJsonObject();
            JsonArray features = root.getAsJsonArray("features");
            long autoId = 1;
            for (JsonElement fe : features) {
                JsonObject f = fe.getAsJsonObject();
                JsonObject geom = f.getAsJsonObject("geometry");
                if (!"Point".equals(geom.get("type").getAsString())) continue;

                JsonArray coords = geom.getAsJsonArray("coordinates"); // [lon, lat]
                double lon = coords.get(0).getAsDouble();
                double lat = coords.get(1).getAsDouble();

                long id = autoId++;
                if (f.has("id")) {
                    try { id = f.get("id").getAsLong(); } catch (Exception ignored) {}
                }
                points.add(new SignalPoint(id, lat, lon));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<SignalPoint> getAll() { return points; }
}
