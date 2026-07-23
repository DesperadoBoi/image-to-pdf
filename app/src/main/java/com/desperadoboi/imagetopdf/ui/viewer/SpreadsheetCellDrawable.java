package com.desperadoboi.imagetopdf.ui.viewer;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.desperadoboi.imagetopdf.document.spreadsheet.SpreadsheetBorder;
import com.desperadoboi.imagetopdf.document.spreadsheet.SpreadsheetCellStyle;

final class SpreadsheetCellDrawable extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final SpreadsheetCellStyle style;
    private final int defaultFillColor;
    private final int gridColor;
    private final float density;

    SpreadsheetCellDrawable(
            SpreadsheetCellStyle style,
            int defaultFillColor,
            int gridColor,
            float density
    ) {
        this.style = style;
        this.defaultFillColor = defaultFillColor;
        this.gridColor = gridColor;
        this.density = density;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(style.getFillColor() == null
                ? defaultFillColor
                : style.getFillColor());
        paint.setPathEffect(null);
        canvas.drawRect(bounds, paint);

        drawSide(canvas, style.getLeftBorder(), bounds.left, bounds.top,
                bounds.left, bounds.bottom, false);
        drawSide(canvas, style.getTopBorder(), bounds.left, bounds.top,
                bounds.right, bounds.top, false);
        drawSide(canvas, style.getRightBorder(), bounds.right, bounds.top,
                bounds.right, bounds.bottom, true);
        drawSide(canvas, style.getBottomBorder(), bounds.left, bounds.bottom,
                bounds.right, bounds.bottom, true);
    }

    private void drawSide(
            Canvas canvas,
            SpreadsheetBorder border,
            float startX,
            float startY,
            float endX,
            float endY,
            boolean drawGridFallback
    ) {
        SpreadsheetBorder.Style borderStyle = border.getStyle();
        if (borderStyle == SpreadsheetBorder.Style.NONE && !drawGridFallback) return;

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(borderWidth(borderStyle));
        paint.setColor(borderStyle == SpreadsheetBorder.Style.NONE
                ? gridColor
                : border.getColor());
        if (borderStyle == SpreadsheetBorder.Style.DASHED) {
            paint.setPathEffect(new DashPathEffect(
                    new float[]{4f * density, 3f * density},
                    0f
            ));
        } else if (borderStyle == SpreadsheetBorder.Style.DOTTED) {
            paint.setPathEffect(new DashPathEffect(
                    new float[]{density, 2f * density},
                    0f
            ));
        } else {
            paint.setPathEffect(null);
        }
        canvas.drawLine(startX, startY, endX, endY, paint);
        if (borderStyle == SpreadsheetBorder.Style.DOUBLE) {
            float inset = 3f * density;
            if (startX == endX) {
                canvas.drawLine(startX - inset, startY, endX - inset, endY, paint);
            } else {
                canvas.drawLine(startX, startY - inset, endX, endY - inset, paint);
            }
        }
    }

    private float borderWidth(SpreadsheetBorder.Style style) {
        if (style == SpreadsheetBorder.Style.HAIR) return Math.max(1f, 0.5f * density);
        if (style == SpreadsheetBorder.Style.MEDIUM
                || style == SpreadsheetBorder.Style.DOUBLE) {
            return Math.max(2f, 1.5f * density);
        }
        if (style == SpreadsheetBorder.Style.THICK) return Math.max(3f, 2.5f * density);
        return Math.max(1f, density);
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
