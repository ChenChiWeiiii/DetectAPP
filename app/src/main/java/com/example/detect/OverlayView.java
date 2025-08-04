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

        for (DetectorMain.Recognition result : results) {
            RectF box = result.getLocation();
            canvas.drawRect(box, boxPaint);

            // 1) 畫標籤 + 信心度（保持預設文字顏色）
            textPaint.setColor(defaultTextColor);
            canvas.drawText(
                    result.getTitle() + " (" + String.format("%.2f", result.getConfidence()) + ")",
                    box.left,
                    box.top - 10,
                    textPaint
            );

            // 2) 如果是 traffic_light，就用動態文字顏色畫出燈號 + 距離
            if ("traffic_light".equals(result.getTitle())) {
                float height = box.height();
                if (height > 0) {
                    float estimatedDistance = 400.0f / height;
                    String color = result.getColor();         // red / yellow / green / unknown
                    // 根據 color 決定文字顏色
                    int textColor;
                    switch (color) {
                        case "red":    textColor = Color.RED;    break;
                        case "yellow": textColor = Color.YELLOW; break;
                        case "green":  textColor = Color.GREEN;  break;
                        default:       textColor = Color.WHITE;  break;
                    }
                    textPaint.setColor(textColor);

                    // 最後畫出「紅綠燈顏色 + 距離」
                    canvas.drawText(
                            String.format("%s  %.1f m", color, estimatedDistance),
                            box.left,
                            box.bottom + 40,
                            textPaint
                    );
                    // 畫完之後重置回預設顏色，以免影響下一個標籤
                    textPaint.setColor(defaultTextColor);
                }
            }
        }
    }
}
