package com.example.detect;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;

public class OverlayView extends View {
    private List<DetectorMain.Recognition> results = new ArrayList<>();
    private List<DetectorMain.Recognition> lastResults = new ArrayList<>();
    private Paint boxPaint;
    private Paint textPaint;
    private final int defaultTextColor = Color.YELLOW;

    private long lastUpdateTime = 0;
    private static final long HOLD_TIME_MS = 500; // 紅框可保留 0.5 秒（防止閃爍）

    public OverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        boxPaint = new Paint();
        boxPaint.setColor(Color.RED);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(6.0f);

        textPaint = new Paint();
        textPaint.setColor(defaultTextColor);
        textPaint.setTextSize(50f);
    }

    // 接收 YOLO 結果
    public void setResults(List<DetectorMain.Recognition> newResults) {
        if (newResults != null && !newResults.isEmpty()) {
            results = new ArrayList<>(newResults);
            lastResults = new ArrayList<>(newResults);
            lastUpdateTime = System.currentTimeMillis();
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        long now = System.currentTimeMillis();

        // 若太久沒有新結果，才清空紅框
        if (now - lastUpdateTime > HOLD_TIME_MS) {
            results.clear();
        }

        // 若暫時沒新結果（YOLO 偵測延遲），就用上一幀結果
        if (results == null || results.isEmpty()) {
            results = new ArrayList<>(lastResults);
        }

        // 清除畫布
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        super.onDraw(canvas);

        if (!(getContext() instanceof MainActivity)) return;
        MainActivity act = (MainActivity) getContext();
        float fPxY = act.getFPxY();
        float scale = Math.max(act.getCurrentScale(), 1e-6f);
        float calib = Math.max(act.getCalibScale(), 1e-6f);
        float dx = act.getCurrentDx();
        float dy = act.getCurrentDy();
        int imgH = Math.max(act.getLastImageHeightPx(), 1);
        float cy = imgH * 0.5f;

        for (DetectorMain.Recognition result : results) {
            RectF box = result.getLocation();
            canvas.drawRect(box, boxPaint);

            // 標籤 + 信心度
            textPaint.setColor(defaultTextColor);
            canvas.drawText(
                    result.getTitle() + " (" + String.format("%.2f", result.getConfidence()) + ")",
                    box.left, box.top - 10, textPaint
            );

            // === 紅綠燈距離 ===
            if ("traffic_light".equals(result.getTitle())) {
                if (fPxY > 0f) {
                    float hView = box.height();
                    float hImg = hView / scale;

                    float d = MainActivity.estimateDistanceForHeightPx(
                            fPxY, calib, hImg, MainActivity.H_TL_LAMP
                    );

                    String color = result.getColor();
                    int textColor = Color.WHITE;
                    if ("red".equals(color)) textColor = Color.RED;
                    else if ("yellow".equals(color)) textColor = Color.YELLOW;
                    else if ("green".equals(color)) textColor = Color.GREEN;

                    textPaint.setColor(textColor);
                    canvas.drawText(
                            String.format("%s 距離： %.1f m", color, d > 0 ? d : 0f),
                            box.left, box.bottom + 40, textPaint
                    );
                    textPaint.setColor(defaultTextColor);
                }
            }

            // === 行人距離 ===
            if (result.getTitle() != null && result.getTitle().toLowerCase().contains("person")) {
                if (fPxY > 0f) {
                    float hView = box.height();
                    float hImg = hView / scale;

                    float d = MainActivity.estimateDistanceForHeightPx(
                            fPxY, calib, hImg, MainActivity.H_PERSON
                    );

                    textPaint.setColor(Color.CYAN);
                    canvas.drawText(
                            String.format("距離：  %.1f m", d > 0 ? d : 0f),
                            box.left, box.bottom + 40, textPaint
                    );
                    textPaint.setColor(defaultTextColor);
                }
            }

            // === 斑馬線距離 ===
            if (result.getTitle() != null && result.getTitle().toLowerCase().contains("crosswalk")) {
                if (fPxY > 0f) {
                    float yBottomView = box.bottom;
                    float yBottomImg = (yBottomView - dy) / scale;

                    float denom = (yBottomImg - cy);
                    float d = (denom > 1f) ? (MainActivity.H_CAMERA * fPxY / denom) : -1f;
                    if (d > 0) d *= calib;

                    textPaint.setColor(Color.WHITE);
                    canvas.drawText(
                            String.format("距離：  %.1f m", d > 0 ? d : 0f),
                            box.left, box.bottom + 40, textPaint
                    );
                    textPaint.setColor(defaultTextColor);
                }
            }
        }

        // 每 16ms 重繪一次，確保紅框持續跟隨
        postInvalidateDelayed(16);
    }
}
