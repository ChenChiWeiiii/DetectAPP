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

            // ===== 行人：幾何（以人高 H_PERSON） =====
            if (result.getTitle() != null && result.getTitle().toLowerCase().contains("person")) {
                if (fPxY > 0f) {
                    float hView = box.height();
                    float hImg  = hView / scale;

                    float d = MainActivity.estimateDistanceForHeightPx(
                            fPxY, calib, hImg, MainActivity.H_PERSON
                    );

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
                    float d = (denom > 1f) ? (MainActivity.H_CAMERA * fPxY / denom) : -1f;
                    if (d > 0) d *= calib; // 套校準比例

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