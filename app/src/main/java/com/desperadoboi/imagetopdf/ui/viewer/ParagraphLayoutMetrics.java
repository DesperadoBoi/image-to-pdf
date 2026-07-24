package com.desperadoboi.imagetopdf.ui.viewer;

import com.desperadoboi.imagetopdf.document.word.WordMeasurementConverter;
import com.desperadoboi.imagetopdf.document.word.WordParagraph;
import com.desperadoboi.imagetopdf.document.word.WordParagraphStyle;
import com.desperadoboi.imagetopdf.document.word.WordRun;

final class ParagraphLayoutMetrics {
    private static final int AUTOMATIC_PARAGRAPH_SPACING_TWIPS = 100;
    private static final float SAFE_FONT_HEIGHT_MULTIPLIER = 1.2f;

    private final int marginTopPixels;
    private final int marginBottomPixels;
    private final int startIndentPixels;
    private final int endIndentPixels;
    private final float lineSpacingExtraPixels;
    private final float lineSpacingMultiplier;
    private final int minimumHeightPixels;

    private ParagraphLayoutMetrics(
            int marginTopPixels,
            int marginBottomPixels,
            int startIndentPixels,
            int endIndentPixels,
            float lineSpacingExtraPixels,
            float lineSpacingMultiplier,
            int minimumHeightPixels
    ) {
        this.marginTopPixels = marginTopPixels;
        this.marginBottomPixels = marginBottomPixels;
        this.startIndentPixels = startIndentPixels;
        this.endIndentPixels = endIndentPixels;
        this.lineSpacingExtraPixels = lineSpacingExtraPixels;
        this.lineSpacingMultiplier = lineSpacingMultiplier;
        this.minimumHeightPixels = minimumHeightPixels;
    }

    static ParagraphLayoutMetrics resolve(
            WordParagraph paragraph,
            WordParagraph previous,
            WordParagraph next,
            WordMeasurementConverter converter
    ) {
        WordParagraphStyle style = paragraph.getStyle();
        float maximumFontPixels = converter.fontPointsToPixels(
                maximumFontPoints(paragraph)
        );
        float naturalLineHeight = Math.max(
                1f,
                maximumFontPixels * SAFE_FONT_HEIGHT_MULTIPLIER
        );

        int currentBefore = spacingTwips(style, true);
        int previousAfter = previous == null
                ? 0
                : spacingTwips(previous.getStyle(), false);
        boolean contextual = previous != null
                && sameStyle(previous.getStyle(), style)
                && (style.isContextualSpacing()
                        || previous.getStyle().isContextualSpacing());
        int topTwips = contextual
                ? 0
                : previous == null
                        ? currentBefore
                        : Math.max(previousAfter, currentBefore);
        int bottomTwips = next == null ? spacingTwips(style, false) : 0;

        int startIndent = resolveIndentPixels(
                style.hasLeftIndent(),
                style.getLeftIndentTwips(),
                style.getStartIndentCharacters(),
                maximumFontPixels,
                converter
        );
        int endIndent = resolveIndentPixels(
                style.hasRightIndent(),
                style.getRightIndentTwips(),
                style.getEndIndentCharacters(),
                maximumFontPixels,
                converter
        );

        float extra = 0f;
        float multiplier = 1f;
        int lineValue = style.getLineSpacingValue();
        if (lineValue > 0) {
            if (style.getLineRule() == WordParagraphStyle.LineRule.AUTO) {
                multiplier = WordMeasurementConverter.autoLineSpacingMultiplier(
                        lineValue
                );
            } else {
                float requested = converter.twipsToPixels(lineValue);
                float safeHeight = Math.max(requested, maximumFontPixels * 1.05f);
                extra = Math.max(0f, safeHeight - naturalLineHeight);
            }
        }
        return new ParagraphLayoutMetrics(
                nonNegativeRound(converter.twipsToPixels(topTwips)),
                nonNegativeRound(converter.twipsToPixels(bottomTwips)),
                Math.max(0, startIndent),
                Math.max(0, endIndent),
                extra,
                multiplier,
                Math.max(1, (int) Math.ceil(naturalLineHeight))
        );
    }

    static float maximumFontPoints(WordParagraph paragraph) {
        float maximum = paragraph.getDefaultRunStyle().getFontSizePoints();
        for (WordRun run : paragraph.getRuns()) {
            maximum = Math.max(maximum, run.getStyle().getFontSizePoints());
        }
        return maximum;
    }

    int getMarginTopPixels() { return marginTopPixels; }
    int getMarginBottomPixels() { return marginBottomPixels; }
    int getStartIndentPixels() { return startIndentPixels; }
    int getEndIndentPixels() { return endIndentPixels; }
    float getLineSpacingExtraPixels() { return lineSpacingExtraPixels; }
    float getLineSpacingMultiplier() { return lineSpacingMultiplier; }
    int getMinimumHeightPixels() { return minimumHeightPixels; }

    private static int spacingTwips(WordParagraphStyle style, boolean before) {
        boolean automatic = before
                ? style.isBeforeAutoSpacing()
                : style.isAfterAutoSpacing();
        if (automatic) return AUTOMATIC_PARAGRAPH_SPACING_TWIPS;
        return Math.max(0, before
                ? style.getSpaceBeforeTwips()
                : style.getSpaceAfterTwips());
    }

    private static boolean sameStyle(
            WordParagraphStyle first,
            WordParagraphStyle second
    ) {
        return !first.getStyleId().isEmpty()
                && first.getStyleId().equals(second.getStyleId());
    }

    private static int resolveIndentPixels(
            boolean hasTwips,
            int twips,
            int characterUnits,
            float fontSizePixels,
            WordMeasurementConverter converter
    ) {
        float pixels = hasTwips
                ? converter.twipsToPixels(twips)
                : converter.characterUnitsToPixels(characterUnits, fontSizePixels);
        return Math.round(pixels);
    }

    private static int nonNegativeRound(float value) {
        return Math.max(0, Math.round(value));
    }
}
