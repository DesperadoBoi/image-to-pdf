package com.desperadoboi.imagetopdf.ui.viewer;

import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.AlignmentSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.MetricAffectingSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.SubscriptSpan;
import android.text.style.SuperscriptSpan;
import android.text.style.TypefaceSpan;
import android.text.style.UnderlineSpan;
import android.view.View;

import androidx.annotation.NonNull;

import com.desperadoboi.imagetopdf.document.word.WordParagraph;
import com.desperadoboi.imagetopdf.document.word.WordParagraphStyle;
import com.desperadoboi.imagetopdf.document.word.WordMeasurementConverter;
import com.desperadoboi.imagetopdf.document.word.WordRun;
import com.desperadoboi.imagetopdf.document.word.WordRunStyle;

final class WordSpannableFactory {
    private WordSpannableFactory() {
    }

    static SpannableStringBuilder create(
            WordParagraph paragraph,
            WordMeasurementConverter converter,
            LinkHandler linkHandler
    ) {
        SpannableStringBuilder text = new SpannableStringBuilder();
        if (!paragraph.getListMarker().isEmpty()) {
            int markerStart = text.length();
            text.append(paragraph.getListMarker());
            applyRunSpans(
                    text,
                    markerStart,
                    text.length(),
                    paragraph.getDefaultRunStyle(),
                    null,
                    converter,
                    linkHandler
            );
        }
        for (WordRun run : paragraph.getRuns()) {
            int start = text.length();
            text.append(run.getText());
            int end = text.length();
            if (end <= start) continue;
            applyRunSpans(
                    text,
                    start,
                    end,
                    run.getStyle(),
                    run.getHyperlink(),
                    converter,
                    linkHandler
            );
        }
        WordParagraphStyle style = paragraph.getStyle();
        int firstLine = 0;
        int rest = 0;
        if (style.hasFirstLineIndent()) {
            firstLine = Math.round(converter.twipsToPixels(
                    style.getFirstLineIndentTwips()
            ));
        } else if (style.hasHangingIndent()) {
            firstLine = -Math.round(converter.twipsToPixels(
                    style.getHangingIndentTwips()
            ));
        } else if (!paragraph.getListMarker().isEmpty()) {
            rest = Math.round(
                    converter.fontPointsToPixels(
                            paragraph.getDefaultRunStyle().getFontSizePoints()
                    ) * 0.55f * paragraph.getListMarker().length()
            );
        }
        if ((firstLine != 0 || rest != 0) && text.length() > 0) {
            text.setSpan(
                    new LeadingMarginSpan.Standard(firstLine, rest),
                    0,
                    text.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
        if (text.length() > 0) {
            text.setSpan(
                    new AlignmentSpan.Standard(alignment(style)),
                    0,
                    text.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
        return text;
    }

    static SpannableStringBuilder createForTable(
            WordParagraph paragraph,
            WordMeasurementConverter converter
    ) {
        SpannableStringBuilder text = create(paragraph, converter, null);
        if (text.length() == 0) {
            text.append('\u200B');
            applyRunSpans(
                    text,
                    0,
                    text.length(),
                    paragraph.getDefaultRunStyle(),
                    null,
                    converter,
                    null
            );
        }
        WordParagraphStyle style = paragraph.getStyle();
        float fontPixels = converter.fontPointsToPixels(
                paragraph.getDefaultRunStyle().getFontSizePoints()
        );
        int startIndent = Math.round(style.hasLeftIndent()
                ? converter.twipsToPixels(style.getLeftIndentTwips())
                : converter.characterUnitsToPixels(
                        style.getStartIndentCharacters(),
                        fontPixels
                ));
        if (startIndent > 0 && text.length() > 0) {
            text.setSpan(
                    new LeadingMarginSpan.Standard(startIndent, startIndent),
                    0,
                    text.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
        return text;
    }

    private static void applyRunSpans(
            SpannableStringBuilder text,
            int start,
            int end,
            WordRunStyle style,
            String hyperlink,
            WordMeasurementConverter converter,
            LinkHandler linkHandler
    ) {
        int typeface = style.isBold() && style.isItalic()
                ? Typeface.BOLD_ITALIC
                : style.isBold() ? Typeface.BOLD : style.isItalic()
                        ? Typeface.ITALIC : Typeface.NORMAL;
        if (typeface != Typeface.NORMAL) {
            set(text, new StyleSpan(typeface), start, end);
        }
        if (style.isUnderline()) set(text, new UnderlineSpan(), start, end);
        if (style.isStrike()) set(text, new StrikethroughSpan(), start, end);
        if (style.getColor() != null) {
            set(text, new ForegroundColorSpan(style.getColor()), start, end);
        }
        if (style.getHighlight() != null) {
            set(text, new BackgroundColorSpan(style.getHighlight()), start, end);
        }
        int sizePixels = Math.max(1, Math.round(
                converter.fontPointsToPixels(style.getFontSizePoints())
        ));
        set(text, new AbsoluteSizeSpan(sizePixels, false), start, end);
        set(text, new TypefaceSpan(style.getFontFamily()), start, end);
        if (style.getVerticalPosition() == WordRunStyle.VerticalPosition.SUBSCRIPT) {
            set(text, new SubscriptSpan(), start, end);
        } else if (style.getVerticalPosition()
                == WordRunStyle.VerticalPosition.SUPERSCRIPT) {
            set(text, new SuperscriptSpan(), start, end);
        } else if (style.getBaselineShiftPoints() != 0f) {
            set(
                    text,
                    new BaselineShiftSpan(Math.round(converter.fontPointsToPixels(
                            style.getBaselineShiftPoints()
                    ))),
                    start,
                    end
            );
        }
        if (hyperlink != null && linkHandler != null) {
            set(text, new SafeLinkSpan(hyperlink, linkHandler), start, end);
        }
    }

    private static android.text.Layout.Alignment alignment(WordParagraphStyle style) {
        if (style.getAlignment() == WordParagraphStyle.Alignment.CENTER) {
            return android.text.Layout.Alignment.ALIGN_CENTER;
        }
        if (style.getAlignment() == WordParagraphStyle.Alignment.RIGHT) {
            return android.text.Layout.Alignment.ALIGN_OPPOSITE;
        }
        return android.text.Layout.Alignment.ALIGN_NORMAL;
    }

    private static void set(
            SpannableStringBuilder text,
            Object span,
            int start,
            int end
    ) {
        text.setSpan(span, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    interface LinkHandler {
        void open(String httpsUri);
    }

    private static final class BaselineShiftSpan extends MetricAffectingSpan {
        private final int shiftPixels;

        private BaselineShiftSpan(int shiftPixels) {
            this.shiftPixels = shiftPixels;
        }

        @Override
        public void updateMeasureState(@NonNull TextPaint textPaint) {
            textPaint.baselineShift -= shiftPixels;
        }

        @Override
        public void updateDrawState(@NonNull TextPaint textPaint) {
            textPaint.baselineShift -= shiftPixels;
        }
    }

    private static final class SafeLinkSpan extends ClickableSpan {
        private final String uri;
        private final LinkHandler handler;

        private SafeLinkSpan(String uri, LinkHandler handler) {
            this.uri = uri;
            this.handler = handler;
        }

        @Override
        public void onClick(@NonNull View widget) {
            handler.open(uri);
        }

        @Override
        public void updateDrawState(@NonNull TextPaint drawState) {
            super.updateDrawState(drawState);
            drawState.setUnderlineText(true);
        }
    }
}
