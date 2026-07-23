package com.desperadoboi.imagetopdf.ui.viewer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatTextView;

import com.desperadoboi.imagetopdf.document.spreadsheet.SpreadsheetCellStyle;

import java.util.ArrayList;
import java.util.List;

final class SpreadsheetCellView extends AppCompatTextView {
    private SpreadsheetCellStyle style = SpreadsheetCellStyle.DEFAULT;
    private boolean overview;

    SpreadsheetCellView(Context context) {
        super(context);
        setIncludeFontPadding(false);
        setLineSpacing(0f, 1f);
        setMinLines(0);
    }

    void setRenderStyle(SpreadsheetCellStyle style, boolean overview) {
        this.style = style;
        this.overview = overview;
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        CharSequence source = getText();
        if (TextUtils.isEmpty(source)) return;

        SpreadsheetRenderMetrics.ClipBounds clip = SpreadsheetRenderMetrics.cellClip(
                getWidth(),
                getHeight(),
                getPaddingLeft(),
                getPaddingTop()
        );
        if (clip.isEmpty()) return;

        TextPaint paint = getPaint();
        paint.setColor(getCurrentTextColor());
        float requestedTextSize = paint.getTextSize();
        Paint.FontMetrics metrics = paint.getFontMetrics();
        float lineHeight = SpreadsheetRenderMetrics.lineHeight(
                metrics.ascent,
                metrics.descent,
                metrics.leading
        );
        if (overview && lineHeight > clip.height()) {
            float fittedSize = SpreadsheetRenderMetrics.fitFontSizeToHeight(
                    requestedTextSize,
                    lineHeight,
                    clip.height()
            );
            if (fittedSize <= 0f) return;
            paint.setTextSize(fittedSize);
            metrics = paint.getFontMetrics();
            lineHeight = SpreadsheetRenderMetrics.lineHeight(
                    metrics.ascent,
                    metrics.descent,
                    metrics.leading
            );
        }

        List<String> lines = style.isWrapText()
                ? wrap(source.toString(), paint, clip.width())
                : singleLine(source.toString(), paint, clip.width());
        int visibleLines = SpreadsheetRenderMetrics.visibleLineCount(
                clip.height(),
                lineHeight,
                lines.size()
        );
        if (visibleLines == 0) {
            paint.setTextSize(requestedTextSize);
            return;
        }
        if (!overview && style.isWrapText() && visibleLines < lines.size()) {
            int last = visibleLines - 1;
            lines.set(last, TextUtils.ellipsize(
                    lines.get(last),
                    paint,
                    clip.width(),
                    TextUtils.TruncateAt.END
            ).toString());
        }

        float baseline = SpreadsheetRenderMetrics.firstBaseline(
                clip.top,
                clip.bottom,
                visibleLines,
                metrics.ascent,
                metrics.descent,
                metrics.leading,
                style.getVerticalAlignment()
        );
        int saveCount = canvas.save();
        canvas.clipRect(clip.left, clip.top, clip.right, clip.bottom);
        for (int line = 0; line < visibleLines; line++) {
            String text = lines.get(line);
            canvas.drawText(
                    text,
                    horizontalPosition(text, paint, clip),
                    baseline + line * lineHeight,
                    paint
            );
        }
        canvas.restoreToCount(saveCount);
        paint.setTextSize(requestedTextSize);
    }

    static int countWrappedLines(String text, TextPaint paint, float width) {
        return wrap(text, paint, width).size();
    }

    private List<String> singleLine(String text, TextPaint paint, float width) {
        List<String> result = new ArrayList<>(1);
        String line = text.replace('\n', ' ');
        if (!overview) {
            line = TextUtils.ellipsize(
                    line,
                    paint,
                    width,
                    TextUtils.TruncateAt.END
            ).toString();
        }
        result.add(line);
        return result;
    }

    private static List<String> wrap(String text, TextPaint paint, float width) {
        List<String> result = new ArrayList<>();
        if (text.isEmpty() || width <= 0f) return result;
        int paragraphStart = 0;
        while (paragraphStart <= text.length()) {
            int paragraphEnd = text.indexOf('\n', paragraphStart);
            if (paragraphEnd < 0) paragraphEnd = text.length();
            appendParagraph(result, text, paragraphStart, paragraphEnd, paint, width);
            if (paragraphEnd == text.length()) break;
            if (paragraphStart == paragraphEnd) result.add("");
            paragraphStart = paragraphEnd + 1;
        }
        if (result.isEmpty()) result.add("");
        return result;
    }

    private static void appendParagraph(
            List<String> result,
            String text,
            int paragraphStart,
            int paragraphEnd,
            TextPaint paint,
            float width
    ) {
        int start = paragraphStart;
        while (start < paragraphEnd) {
            int fit = paint.breakText(
                    text,
                    start,
                    paragraphEnd,
                    true,
                    width,
                    null
            );
            if (fit <= 0) return;
            int end = Math.min(paragraphEnd, start + fit);
            if (end < paragraphEnd) {
                int whitespace = lastWhitespace(text, start, end);
                if (whitespace > start) end = whitespace;
            }
            int next = skipWhitespace(text, end, paragraphEnd);
            if (paragraphEnd - next == 1 && end - start > 1) {
                end--;
                next = skipWhitespace(text, end, paragraphEnd);
            }
            int trimmedEnd = end;
            while (trimmedEnd > start && Character.isWhitespace(text.charAt(trimmedEnd - 1))) {
                trimmedEnd--;
            }
            result.add(text.substring(start, trimmedEnd));
            start = Math.max(next, end);
        }
    }

    private static int lastWhitespace(String text, int start, int end) {
        for (int index = end - 1; index > start; index--) {
            if (Character.isWhitespace(text.charAt(index))) return index;
        }
        return -1;
    }

    private static int skipWhitespace(String text, int start, int end) {
        int index = start;
        while (index < end && Character.isWhitespace(text.charAt(index))) index++;
        return index;
    }

    private float horizontalPosition(
            String text,
            TextPaint paint,
            SpreadsheetRenderMetrics.ClipBounds clip
    ) {
        int horizontalGravity = Gravity.getAbsoluteGravity(
                getGravity(),
                getLayoutDirection()
        ) & Gravity.HORIZONTAL_GRAVITY_MASK;
        float textWidth = paint.measureText(text);
        if (horizontalGravity == Gravity.CENTER_HORIZONTAL) {
            return clip.left + Math.max(0f, (clip.width() - textWidth) / 2f);
        }
        if (horizontalGravity == Gravity.RIGHT) {
            return clip.right - textWidth;
        }
        return clip.left;
    }
}
