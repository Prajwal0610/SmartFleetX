package com.example.smartfleetx.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import com.example.smartfleetx.ai.YOLOv8Detector;

import java.util.ArrayList;
import java.util.List;

public class OverlayView extends View {

    private List<YOLOv8Detector.Recognition> results = new ArrayList<>();
    private final Paint boxPaint = new Paint();
    private final Paint textPaint = new Paint();

    public OverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        
        boxPaint.setColor(Color.RED);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(4.0f);

        textPaint.setColor(Color.RED);
        textPaint.setTextSize(36.0f);
        textPaint.setStyle(Paint.Style.FILL);
    }

    public void setResults(List<YOLOv8Detector.Recognition> results) {
        // Skip redraw if the results haven't meaningfully changed
        // (same number of detections with same class IDs) to avoid
        // a forced GPU invalidate every second when scene is static.
        if (resultsAreSame(this.results, results)) return;
        this.results = results;
        invalidate();
    }

    private boolean resultsAreSame(List<YOLOv8Detector.Recognition> a, List<YOLOv8Detector.Recognition> b) {
        if (a == null || b == null) return false;
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (a.get(i).classId != b.get(i).classId) return false;
        }
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        float scaleX = getWidth() / 640.0f; // Input size was 640
        float scaleY = getHeight() / 640.0f;

        for (YOLOv8Detector.Recognition res : results) {
            RectF loc = new RectF(
                res.location.left * scaleX,
                res.location.top * scaleY,
                res.location.right * scaleX,
                res.location.bottom * scaleY
            );
            
            canvas.drawRect(loc, boxPaint);
            canvas.drawText(
                res.title + " " + String.format("%.2f", res.confidence),
                loc.left,
                loc.top - 10,
                textPaint
            );
        }
    }
}
