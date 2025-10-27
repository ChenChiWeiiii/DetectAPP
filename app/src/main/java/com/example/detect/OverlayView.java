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
import java.util.Iterator;
import java.util.List;

public class OverlayView extends View {
    private List<DetectorMain.Recognition> stableResults = new ArrayList<>();
    private Paint boxPaint;
    private Paint textPaint;
    private final int defaultTextColor = Color.YELLOW;

    // 每個紅框的生命週期（若連續幾幀沒偵測到就刪除）
    private static final int MAX_MISSING_FRAMES = 10;
    private int frameCounter = 0;

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

    // 🔴 YOLO 偵測結果輸入
    public void setResults(List<DetectorMain.Recognition> newResults) {
        frameCounter++;

        if (newResults == null || newResults.isEmpty()) {
            // 沒新結果 → 累計缺失幀數，不立即刪除
            if (frameCounter > MAX_MISSING_FRAMES) {
                stableResults.clear();
                frameCounter = 0;
            }
            invalidate();
            return;
        }

        // 比對新舊框（用 IoU 判斷是否同一個物件）
        List<DetectorMain.Recognition> updated = new ArrayList<>();
        for (DetectorMain.Recognition cur : newResults) {
            RectF curBox = cur.getLocation();
            boolean matched = false;
            for (DetectorMain.Recognition old : stableResults) {
                float iou = boxIoU(old.getLocation(), curBox);
                if (iou > 0.3f) {
                    // 更新舊框位置與顏色，平滑移動
                    RectF oldBox = old.getLocation();
                    oldBox.left   = 0.7f * oldBox.left   + 0.3f * curBox.left;
                    oldBox.top    = 0.7f * oldBox.top    + 0.3f * curBox.top;
                    oldBox.right  = 0.7f * oldBox.right  + 0.3f * curBox.right;
                    oldBox.bottom = 0.7f * oldBox.bottom + 0.3f * curBox.bottom;
                    old.setColor(cur.getColor());
                    matched = true;
                    updated.add(old);
                    break;
                }
            }
            if (!matched) {
                updated.add(cur);
            }
        }

        stableResults = updated;
        frameCounter = 0;
        invalidate();
    }

    private float boxIoU(RectF a, RectF b) {
        float interLeft = Math.max(a.left, b.left);
        float interTop = Math.max(a.top, b.top);
        float interRight = Math.min(a.right, b.right);
        float interBottom = Math.min(a.bottom, b.bottom);
        float interArea = Math.max(0, interRight - interLeft) * Math.max(0, interBottom - interTop);
        float unionArea = a.width() * a.height() + b.width() * b.height() - interArea;
        return unionArea > 0 ? interArea / unionArea : 0f;
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
        float cy = imgH * 0.5f;

        Iterator<DetectorMain.Recognition> iterator = stableResults.iterator();
        while (iterator.hasNext()) {
            DetectorMain.Recognition result = iterator.next();
            RectF box = result.getLocation();

            // 🔴 繪製框線
            canvas.drawRect(box, boxPaint);

            // 🔵 標題文字
            textPaint.setColor(defaultTextColor);
            canvas.drawText(
                    result.getTitle() + " (" + String.format("%.2f", result.getConfidence()) + ")",
                    box.left, box.top - 10, textPaint
            );

            // 🔶 顯示距離資訊
            if ("traffic_light".equals(result.getTitle())) {
                if (fPxY > 0f) {
                    float hView = box.height();
                    float hImg = hView / scale;
                    float d = MainActivity.estimateDistanceForHeightPx(fPxY, calib, hImg, MainActivity.H_TL_LAMP);
                    String color = result.getColor();
                    int textColor;
                    if ("red".equals(color)) textColor = Color.RED;
                    else if ("yellow".equals(color)) textColor = Color.YELLOW;
                    else if ("green".equals(color)) textColor = Color.GREEN;
                    else textColor = Color.WHITE;
                    textPaint.setColor(textColor);
                    canvas.drawText(String.format("%s 距離： %.1f m", color, Math.max(d, 0f)),
                            box.left, box.bottom + 40, textPaint);
                }
            } else if (result.getTitle() != null && result.getTitle().toLowerCase().contains("person")) {
                if (fPxY > 0f) {
                    float hView = box.height();
                    float hImg = hView / scale;
                    float d = MainActivity.estimateDistanceForHeightPx(fPxY, calib, hImg, MainActivity.H_PERSON);
                    textPaint.setColor(Color.CYAN);
                    canvas.drawText(String.format("距離： %.1f m", Math.max(d, 0f)),
                            box.left, box.bottom + 40, textPaint);
                }
            } else if (result.getTitle() != null && result.getTitle().toLowerCase().contains("crosswalk")) {
                if (fPxY > 0f) {
                    float yBottomView = box.bottom;
                    float yBottomImg = (yBottomView - dy) / scale;
                    float denom = (yBottomImg - cy);
                    float d = (denom > 1f) ? (MainActivity.H_CAMERA * fPxY / denom) : -1f;
                    if (d > 0) d *= calib;
                    textPaint.setColor(Color.WHITE);
                    canvas.drawText(String.format("距離： %.1f m", Math.max(d, 0f)),
                            box.left, box.bottom + 40, textPaint);
                }
            }
        }

        // 每幀重繪
        postInvalidateDelayed(16);
    }
}
