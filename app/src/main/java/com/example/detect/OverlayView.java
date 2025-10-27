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
    private Paint boxPaint;
    private Paint textPaint;
    private final int defaultTextColor = Color.YELLOW;

    // === 新增：記錄上次更新時間 ===
    private long lastUpdateTime = 0;
    private static final long MAX_HOLD_MS = 500; // 若 0.5 秒沒新結果就清除（可自行調整）

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

    public void setResults(List<DetectorMain.Recognition> results) {
        // 若 YOLO 暫時沒有結果，不更新畫面（避免閃爍）
        if (results == null || results.isEmpty()) {
            postInvalidateDelayed(16);
            return;
        }

        this.results = new ArrayList<>(results);
        lastUpdateTime = System.currentTimeMillis(); // 更新時間戳
        invalidate();
        Log.d("OverlayView", "Result：" + this.results.size());
    }

    @Override
    protected void onDraw(Canvas canvas) {
        long now = System.currentTimeMillis();

        // 若超過 0.5 秒沒更新 → 清空畫面
        if (now - lastUpdateTime > MAX_HOLD_MS) {
            results.clear();
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            super.onDraw(canvas);
            postInvalidateDelayed(33);
            return;
        }

        // 若暫時沒有新結果 → 保留上一幀紅框，避免閃爍
        if (results == null || results.isEmpty()) {
            postInvalidateDelayed(16);
            return;
        }

        // 有結果才清畫布重新繪製
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
            textPaint.setColor(defaultTextColor);
            canvas.drawText(
                    result.getTitle() + " (" + String.format("%.2f", result.getConfidence()) + ")",
                    box.left, box.top - 10, textPaint
            );

            if ("traffic_light".equals(result.getTitle())) {
                if (fPxY > 0f) {
                    float hView = box.height();
                    float hImg  = hView / scale;
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

            if (result.getTitle() != null && result.getTitle().toLowerCase().contains("person")) {
                if (fPxY > 0f) {
                    float hView = box.height();
                    float hImg  = hView / scale;
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

            if (result.getTitle() != null && result.getTitle().toLowerCase().contains("crosswalk")) {
                if (fPxY > 0f) {
                    float yBottomView = box.bottom;
                    float yBottomImg  = (yBottomView - dy) / scale;
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
    }
}
