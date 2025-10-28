package com.example.detect;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;

public class OverlayView extends View {
    private List<DetectorMain.Recognition> results = new ArrayList<>();
    private Paint boxPaint;
    private Paint textPaint;
    // 紀錄預設文字顏色
    private final int defaultTextColor = Color.YELLOW;
    // 平滑框紀錄
    private final java.util.Map<String, RectF> lastBoxes = new java.util.HashMap<>();


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
        this.results = results;
        invalidate();
        Log.d("OverlayView", "Result：" + results.size());
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (!(getContext() instanceof MainActivity)) return;
        MainActivity act = (MainActivity) getContext();
        float fPxY = act.getFPxY();
        float scale = Math.max(act.getCurrentScale(), 1e-6f);
        float calib = Math.max(act.getCalibScale(), 1e-6f);
        float dx = act.getCurrentDx();
        float dy = act.getCurrentDy();
        int imgH = Math.max(act.getLastImageHeightPx(), 1);
        float cy = imgH * 0.5f;  // 主點 y 近似影像高度中線

        for (DetectorMain.Recognition result : results) {
            RectF box = result.getLocation();
            canvas.drawRect(box, boxPaint);

            // 標籤 + 信心度
            textPaint.setColor(defaultTextColor);
            canvas.drawText(
                    result.getTitle() + " (" + String.format("%.2f", result.getConfidence()) + ")",
                    box.left, box.top - 10, textPaint
            );

            // ===== 紅綠燈：保持你原本（幾何 + 顏色） =====
            if ("traffic_light".equals(result.getTitle())) {
                if (fPxY > 0f) {
                    float hView = box.height();
                    float hImg  = hView / scale;

                    float estimatedD = MainActivity.estimateDistanceForHeightPx(
                            fPxY, calib, hImg, MainActivity.H_TL_LAMP
                    );
                    float d = smoothDistance(result.getTitle(), estimatedD);

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

            // ===== 行人：幾何（以人高 H_PERSON） =====
            if (result.getTitle() != null && result.getTitle().toLowerCase().contains("person")) {
                if (fPxY > 0f) {
                    float hView = box.height();
                    float hImg  = hView / scale;

                    float estimatedD = MainActivity.estimateDistanceForHeightPx(
                            fPxY, calib, hImg, MainActivity.H_PERSON
                    );
                    float d = smoothDistance(result.getTitle(), estimatedD);

                    // 顏色：青色（或白色）
                    textPaint.setColor(Color.CYAN);
                    canvas.drawText(
                            String.format("距離：  %.1f m", d > 0 ? d : 0f),
                            box.left, box.bottom + 40, textPaint
                    );
                    textPaint.setColor(defaultTextColor);
                }
            }

            // ===== 斑馬線：地平面幾何（用框底 y） =====
            if (result.getTitle() != null && result.getTitle().toLowerCase().contains("crosswalk")) {
                if (fPxY > 0f) {
                    // 把 view 座標還原成影像座標：y_img = (y_view - dy) / scale
                    float yBottomView = box.bottom;
                    float yBottomImg  = (yBottomView - dy) / scale;

                    // d ≈ (H_CAMERA * fPy) / (y_img_bottom - c_y) ；需 y_img_bottom > c_y
                    float denom = (yBottomImg - cy);
                    float estimatedD = (denom > 1f) ? (MainActivity.H_CAMERA * fPxY / denom) : -1f;
                    if (estimatedD > 0) estimatedD *= calib;
                    float d = smoothDistance(result.getTitle(), estimatedD);

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

    private RectF smoothBox(String key, RectF newBox) {
        RectF old = lastBoxes.get(key);
        if (old == null) {
            lastBoxes.put(key, new RectF(newBox));
            return newBox;
        }

        // 若偏移太大（失鎖），直接更新
        float maxShift = 0.3f * old.height();
        if (Math.abs(newBox.centerX() - old.centerX()) > maxShift ||
                Math.abs(newBox.centerY() - old.centerY()) > maxShift) {
            lastBoxes.put(key, new RectF(newBox));
            return newBox;
        }

        // 否則進行平滑插值
        float alpha = 0.75f; // 越大越穩定（但反應較慢）
        RectF smoothed = new RectF(
                old.left * alpha + newBox.left * (1 - alpha),
                old.top * alpha + newBox.top * (1 - alpha),
                old.right * alpha + newBox.right * (1 - alpha),
                old.bottom * alpha + newBox.bottom * (1 - alpha)
        );
        lastBoxes.put(key, new RectF(smoothed));
        return smoothed;
    }

    private final java.util.Map<String, Float> lastDistance = new java.util.HashMap<>();

    private float smoothDistance(String key, float newD) {
        if (newD <= 0) return newD;
        Float old = lastDistance.get(key);
        if (old == null) {
            lastDistance.put(key, newD);
            return newD;
        }
        float alpha = 0.7f; // 越大越穩定（但變化慢）
        float smoothed = old * alpha + newD * (1 - alpha);
        lastDistance.put(key, smoothed);
        return smoothed;
    }

    private final Handler redrawHandler = new Handler(Looper.getMainLooper());
    private static final long FRAME_INTERVAL_MS = 16; // 約 60FPS

    private final Runnable redrawLoop = new Runnable() {
        @Override public void run() {
            invalidate();
            redrawHandler.postDelayed(this, FRAME_INTERVAL_MS);
        }
    };

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        redrawHandler.post(redrawLoop);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        redrawHandler.removeCallbacks(redrawLoop);
    }

}