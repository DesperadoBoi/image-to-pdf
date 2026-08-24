package com.desperadoboi.imagetopdf.ui.watermark;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;

public final class WatermarkPainter {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);

    public WatermarkPainter() {
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
    }

    public void draw(
            Canvas canvas,
            float pageWidth,
            float pageHeight,
            PdfWatermarkOptions options
    ) {
        if (canvas == null || options == null || !options.hasValidText()
                || pageWidth <= 0f || pageHeight <= 0f) {
            return;
        }
        String text = options.getText().trim();
        paint.setColor(options.getColor().getArgb());
        paint.setAlpha(Math.round(options.getOpacity() * 255f));
        paint.setTextSize(options.getSizePoints());
        Paint.FontMetrics metrics = paint.getFontMetrics();
        WatermarkGeometry.Layout layout = WatermarkGeometry.calculate(
                pageWidth,
                pageHeight,
                paint.measureText(text),
                metrics.descent - metrics.ascent,
                options.getRotationDegrees(),
                options.getStyle(),
                options.getPosition()
        );
        if (layout.getPlacements().isEmpty()) return;

        paint.setTextSize(options.getSizePoints() * layout.getTextScale());
        metrics = paint.getFontMetrics();
        float baselineOffset = -((metrics.ascent + metrics.descent) / 2f);
        int checkpoint = canvas.save();
        canvas.clipRect(0f, 0f, pageWidth, pageHeight);
        for (WatermarkGeometry.Placement placement : layout.getPlacements()) {
            int placementCheckpoint = canvas.save();
            canvas.rotate(
                    options.getRotationDegrees(),
                    placement.getCenterX(),
                    placement.getCenterY()
            );
            canvas.drawText(
                    text,
                    placement.getCenterX(),
                    placement.getCenterY() + baselineOffset,
                    paint
            );
            canvas.restoreToCount(placementCheckpoint);
        }
        canvas.restoreToCount(checkpoint);
    }
}
