package com.desperadoboi.imagetopdf.ui.viewer;

import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.TextPaint;

import com.desperadoboi.imagetopdf.document.spreadsheet.SpreadsheetCellStyle;

final class SpreadsheetPaintCache {
    static final int MAXIMUM_STYLE_COUNT = 256;

    private final int defaultFillColor;
    private final int defaultTextColor;
    private final float defaultTextSizePx;
    private final float scaledDensity;
    private final SpreadsheetBoundedLruCache<Integer, StylePaints> cache =
            new SpreadsheetBoundedLruCache<>(MAXIMUM_STYLE_COUNT);

    SpreadsheetPaintCache(
            int defaultFillColor,
            int defaultTextColor,
            float defaultTextSizePx,
            float scaledDensity
    ) {
        this.defaultFillColor = defaultFillColor;
        this.defaultTextColor = defaultTextColor;
        this.defaultTextSizePx = defaultTextSizePx;
        this.scaledDensity = scaledDensity;
    }

    StylePaints get(SpreadsheetCellStyle style) {
        int key = cacheKey(style);
        StylePaints paints = cache.get(key);
        if (paints != null && paints.style == style) return paints;
        paints = create(style);
        cache.put(key, paints);
        return paints;
    }

    int styleKey(SpreadsheetCellStyle style) {
        return cacheKey(style);
    }

    int size() {
        return cache.size();
    }

    void clear() {
        cache.clear();
    }

    private StylePaints create(SpreadsheetCellStyle style) {
        Paint fillPaint = new Paint();
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(style.getFillColor() == null
                ? defaultFillColor
                : style.getFillColor());

        TextPaint textPaint = new TextPaint(
                Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG
        );
        textPaint.setColor(style.getFontColor() == null
                ? defaultTextColor
                : style.getFontColor());
        textPaint.setTextSize(style.getFontSizePoints() > 0f
                ? style.getFontSizePoints() * scaledDensity
                : defaultTextSizePx);
        textPaint.setTypeface(Typeface.create(
                "sans-serif",
                style.isBold() && style.isItalic()
                        ? Typeface.BOLD_ITALIC
                        : style.isBold()
                                ? Typeface.BOLD
                                : style.isItalic() ? Typeface.ITALIC : Typeface.NORMAL
        ));
        textPaint.setUnderlineText(style.isUnderline());
        return new StylePaints(style, fillPaint, textPaint, textPaint.getTextSize());
    }

    private static int cacheKey(SpreadsheetCellStyle style) {
        int styleId = style.getStyleId();
        return styleId != 0 || style == SpreadsheetCellStyle.DEFAULT
                ? styleId
                : System.identityHashCode(style);
    }

    static final class StylePaints {
        private final SpreadsheetCellStyle style;
        final Paint fillPaint;
        final TextPaint textPaint;
        final float baseTextSizePx;

        private StylePaints(
                SpreadsheetCellStyle style,
                Paint fillPaint,
                TextPaint textPaint,
                float baseTextSizePx
        ) {
            this.style = style;
            this.fillPaint = fillPaint;
            this.textPaint = textPaint;
            this.baseTextSizePx = baseTextSizePx;
        }
    }
}
