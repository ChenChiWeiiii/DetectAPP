package com.example.detect;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
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
    // 紀錄預設文字顏色
    private final int defaultTextColor = Color.YELLOW;

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

        for (DetectorMain.Recognition result : results) {
            RectF box = result.getLocation();
            canvas.drawRect(box, boxPaint);

            // 標籤 + 信心度
            textPaint.setColor(defaultTextColor);
            canvas.drawText(
                    result.getTitle() + " (" + String.format("%.2f", result.getConfidence()) + ")",
                    box.left, box.top - 10, textPaint
            );

            // ===== 紅綠燈：只顯示顏色，不顯示距離 =====
            if ("traffic_light".equals(result.getTitle())) {
                String color = result.getColor();
                int textColor = Color.WHITE;
                if ("red".equals(color)) textColor = Color.RED;
                else if ("yellow".equals(color)) textColor = Color.YELLOW;
                else if ("green".equals(color)) textColor = Color.GREEN;

                textPaint.setColor(textColor);
                canvas.drawText(color, box.left, box.bottom + 40, textPaint);
                textPaint.setColor(defaultTextColor);
            }

            // ===== 行人：顯示距離 =====
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

            // ===== 斑馬線：不顯示任何額外資訊 =====
            // (此處刻意留空，不顯示距離)
        }
    }
}
