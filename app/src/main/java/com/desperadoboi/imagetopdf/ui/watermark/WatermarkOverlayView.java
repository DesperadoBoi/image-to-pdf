package com.desperadoboi.imagetopdf.ui.watermark;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public final class WatermarkOverlayView extends View {
    private final WatermarkPainter painter = new WatermarkPainter();
    private PdfWatermarkOptions options;
    private int pageWidthPoints;
    private int pageHeightPoints;

    public WatermarkOverlayView(Context context) {
        this(context, null);
    }

    public WatermarkOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setClickable(false);
        setFocusable(false);
    }

    public void setPageGeometry(int widthPoints, int heightPoints) {
        pageWidthPoints = Math.max(0, widthPoints);
        pageHeightPoints = Math.max(0, heightPoints);
        invalidate();
    }

    public void setOptions(PdfWatermarkOptions options) {
        this.options = options;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (options == null || pageWidthPoints <= 0 || pageHeightPoints <= 0) return;
        float scale = Math.min(
                getWidth() / (float) pageWidthPoints,
                getHeight() / (float) pageHeightPoints
        );
        if (!Float.isFinite(scale) || scale <= 0f) return;
        float left = (getWidth() - pageWidthPoints * scale) / 2f;
        float top = (getHeight() - pageHeightPoints * scale) / 2f;
        int checkpoint = canvas.save();
        canvas.translate(left, top);
        canvas.scale(scale, scale);
        painter.draw(canvas, pageWidthPoints, pageHeightPoints, options);
        canvas.restoreToCount(checkpoint);
    }
}
