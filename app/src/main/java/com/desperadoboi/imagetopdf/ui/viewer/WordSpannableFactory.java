package com.desperadoboi.imagetopdf.ui.viewer;

import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.LeadingMarginSpan;
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
import com.desperadoboi.imagetopdf.document.word.WordRun;
import com.desperadoboi.imagetopdf.document.word.WordRunStyle;

final class WordSpannableFactory {
    private WordSpannableFactory() {
    }

    static SpannableStringBuilder create(
            WordParagraph paragraph,
            float density,
            LinkHandler linkHandler
    ) {
        SpannableStringBuilder text = new SpannableStringBuilder();
        if (!paragraph.getListMarker().isEmpty()) {
            text.append(paragraph.getListMarker());
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
                    run,
                    paragraph.getStyle(),
                    linkHandler
            );
        }
        WordParagraphStyle style = paragraph.getStyle();
        int firstLine = Math.round(
                (style.getFirstLineIndentTwips() - style.getHangingIndentTwips())
                        * density / 9f
        );
        if (firstLine != 0 && text.length() > 0) {
            text.setSpan(
                    new LeadingMarginSpan.Standard(firstLine, 0),
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
            WordRun run,
            WordParagraphStyle paragraphStyle,
            LinkHandler linkHandler
    ) {
        WordRunStyle style = run.getStyle();
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
        float size = style.getFontSizePoints();
        int heading = paragraphStyle.getHeadingLevel();
        if (heading > 0 && size <= 11f) {
            size = heading == 1 ? 24f : heading == 2 ? 20f
                    : heading == 3 ? 18f : 15f;
        }
        set(text, new AbsoluteSizeSpan(Math.max(8, Math.round(size)), true), start, end);
        set(text, new TypefaceSpan(style.getFontFamily()), start, end);
        if (style.getVerticalPosition() == WordRunStyle.VerticalPosition.SUBSCRIPT) {
            set(text, new SubscriptSpan(), start, end);
        } else if (style.getVerticalPosition()
                == WordRunStyle.VerticalPosition.SUPERSCRIPT) {
            set(text, new SuperscriptSpan(), start, end);
        }
        if (run.getHyperlink() != null && linkHandler != null) {
            set(text, new SafeLinkSpan(run.getHyperlink(), linkHandler), start, end);
        }
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
