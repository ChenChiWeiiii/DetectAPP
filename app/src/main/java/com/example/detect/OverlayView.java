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
        textPaint.setColor(Color.YELLOW);
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
        for (DetectorMain.Recognition result : results) {
            RectF box = result.getLocation();
            canvas.drawRect(box, boxPaint);

            // 顯示標籤 + 信心度
            canvas.drawText(result.getTitle() + " (" + String.format("%.2f", result.getConfidence()) + ")",
                    box.left, box.top - 10, textPaint);

            // 顯示燈號和距離
            if ("traffic_light".equals(result.getTitle())) {
                float height = box.height();
                if (height > 0) {
                    float estimatedDistance = 400.0f / height;
                    String color = result.getColor();
                    if (color == null || "unknown".equals(color)) {
                        color = "?";
                    }
                    canvas.drawText(String.format("%s  %.1f m", color, estimatedDistance),
                            box.left, box.bottom + 40, textPaint);
                }
            }

        }
    }

}
