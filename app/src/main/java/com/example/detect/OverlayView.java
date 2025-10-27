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

    // 時間控制
    private long lastUpdateTime = 0;
    private static final long MAX_HOLD_MS = 500; // 紅框保留時間 0.5 秒

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

    // ✅ 用於接收 YOLO 結果
    public void setResults(List<DetectorMain.Recognition> newResults) {
        long now = System.currentTimeMillis();

        if (newResults == null || newResults.isEmpty()) {
            // 沒有新結果：只重畫上一幀，不清空
            postInvalidateDelayed(16);
            return;
        }

        // 更新時間與緩衝
        this.results = new ArrayList<>(newResults);
        this.lastResults = this.results;
        lastUpdateTime = now;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        long now = System.currentTimeMillis();

        // ✅ 若超過 0.5 秒沒新結果，就清空畫面
        if (now - lastUpdateTime > MAX_HOLD_MS) {
            results.clear();
            lastResults.clear();
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            postInvalidateDelayed(33);
            return;
        }

        // ✅ 若暫時沒有新結果，用上一幀的結果
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

            if ("traffic_light".equals(result.getTitle())) {
                if (fPxY > 0f) {
                    float hView = box.height();
                    float hImg = hView / scale;
                    float d = MainActivity.estimateDistanceForHeightPx(
                            fPxY, calib, hImg, MainActivity.H_TL_LAMP
                    );
                    String color = result.getColor();
                    int textColor;
                    if ("red".equals(color)) {
                        textColor = Color.RED;
                    } else if ("yellow".equals(color)) {
                        textColor = Color.YELLOW;
                    } else if ("green".equals(color)) {
                        textColor = Color.GREEN;
                    } else {
                        textColor = Color.WHITE;
                    }
                    textPaint.setColor(textColor);
                    canvas.drawText(
                            String.format("%s 距離： %.1f m", color, d > 0 ? d : 0f),
                            box.left, box.bottom + 40, textPaint
                    );
                }
            }
        }

        // ✅ 持續重繪畫面
        postInvalidateDelayed(16);
    }
}
