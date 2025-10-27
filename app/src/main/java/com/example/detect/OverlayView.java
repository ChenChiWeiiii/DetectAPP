package com.example.detect;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

public class OverlayView extends View {
    private List<DetectorMain.Recognition> results = new ArrayList<>();
    private List<DetectorMain.Recognition> lastResults = new ArrayList<>();
    private Paint boxPaint;
    private Paint textPaint;
    private final int defaultTextColor = Color.YELLOW;

    // 時間控制
    private long lastUpdateTime = 0;
    private static final long MAX_HOLD_MS = 1000; // 1 秒沒新結果才清除（更穩定）

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

    // ✅ 接收 YOLO 結果
    public void setResults(List<DetectorMain.Recognition> newResults) {
        long now = System.currentTimeMillis();

        if (newResults == null || newResults.isEmpty()) {
            // 沒有新結果：維持上一幀
            postInvalidateDelayed(16);
            return;
        }

        // 平滑更新位置：取前一幀與新結果的平均，防止紅框跳動
        if (!lastResults.isEmpty()) {
            for (int i = 0; i < Math.min(newResults.size(), lastResults.size()); i++) {
                RectF prev = lastResults.get(i).getLocation();
                RectF cur = newResults.get(i).getLocation();
                // 平滑過渡（0.7 前一幀 + 0.3 新一幀）
                cur.left = 0.7f * prev.left + 0.3f * cur.left;
                cur.top = 0.7f * prev.top + 0.3f * cur.top;
                cur.right = 0.7f * prev.right + 0.3f * cur.right;
                cur.bottom = 0.7f * prev.bottom + 0.3f * cur.bottom;
            }
        }

        // 更新時間與結果緩衝
        this.results = new ArrayList<>(newResults);
        this.lastResults = new ArrayList<>(newResults);
        lastUpdateTime = now;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        long now = System.currentTimeMillis();

        // ✅ 若超過 1 秒沒新結果，表示物件真的離開畫面 → 清空紅框
        if (now - lastUpdateTime > MAX_HOLD_MS) {
            results.clear();
            lastResults.clear();
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            postInvalidateDelayed(33);
            return;
        }

        // ✅ 若暫時沒有新結果，用上一幀的框繼續顯示（防止閃爍）
        if (results == null || results.isEmpty()) {
            results = new ArrayList<>(lastResults);
        }

        // ✅ 清畫布再畫
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

            // === ✅ 以下是妳原本的距離顯示程式碼 ===
            if ("traffic_light".equals(result.getTitle())) {
                if (fPxY > 0f) {
                    float hView = box.height();
                    float hImg  = hView / scale;
                    float d = MainActivity.estimateDistanceForHeightPx(
                            fPxY, calib, hImg, MainActivity.H_TL_LAMP
                    );
                    String color = result.getColor();
                    int textColor;
                    if ("red".equals(color)) textColor = Color.RED;
                    else if ("yellow".equals(color)) textColor = Color.YELLOW;
                    else if ("green".equals(color)) textColor = Color.GREEN;
                    else textColor = Color.WHITE;
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

        // ✅ 讓畫面穩定持續重繪
        postInvalidateDelayed(16);
    }
}
