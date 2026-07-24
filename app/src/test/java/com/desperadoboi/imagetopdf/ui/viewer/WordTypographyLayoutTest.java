package com.desperadoboi.imagetopdf.ui.viewer;

import com.desperadoboi.imagetopdf.document.word.WordMeasurementConverter;
import com.desperadoboi.imagetopdf.document.word.WordParagraph;
import com.desperadoboi.imagetopdf.document.word.WordParagraphStyle;
import com.desperadoboi.imagetopdf.document.word.WordRun;
import com.desperadoboi.imagetopdf.document.word.WordRunStyle;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class WordTypographyLayoutTest {
    private static final float DELTA = 0.001f;

    @Test
    public void collapsesAdjacentParagraphSpacingAndConvertsIndentsFromTwips() {
        WordMeasurementConverter converter =
                new WordMeasurementConverter(144f, 1f, 1f);
        WordParagraph previous = paragraph(
                new WordParagraphStyle.Builder()
                        .setStyleId("Body")
                        .setSpaceAfterTwips(240)
                        .build(),
                12f,
                "before"
        );
        WordParagraph current = paragraph(
                new WordParagraphStyle.Builder()
                        .setStyleId("Body")
                        .setSpaceBeforeTwips(120)
                        .setSpaceAfterTwips(360)
                        .setLeftIndentTwips(720)
                        .setRightIndentTwips(360)
                        .setLineSpacingValue(360)
                        .setLineRule(WordParagraphStyle.LineRule.AUTO)
                        .build(),
                12f,
                "current"
        );
        ParagraphLayoutMetrics metrics = ParagraphLayoutMetrics.resolve(
                current,
                previous,
                null,
                converter
        );

        assertEquals(24, metrics.getMarginTopPixels());
        assertEquals(36, metrics.getMarginBottomPixels());
        assertEquals(72, metrics.getStartIndentPixels());
        assertEquals(36, metrics.getEndIndentPixels());
        assertEquals(1.5f, metrics.getLineSpacingMultiplier(), DELTA);
        assertEquals(0f, metrics.getLineSpacingExtraPixels(), DELTA);
    }

    @Test
    public void contextualAndAutomaticSpacingAreDeterministic() {
        WordMeasurementConverter converter =
                new WordMeasurementConverter(144f, 1f, 1f);
        WordParagraph previous = paragraph(
                new WordParagraphStyle.Builder()
                        .setStyleId("List")
                        .setAfterAutoSpacing(true)
                        .build(),
                11f,
                "one"
        );
        WordParagraph current = paragraph(
                new WordParagraphStyle.Builder()
                        .setStyleId("List")
                        .setBeforeAutoSpacing(true)
                        .setContextualSpacing(true)
                        .build(),
                11f,
                "two"
        );
        ParagraphLayoutMetrics contextual = ParagraphLayoutMetrics.resolve(
                current,
                previous,
                null,
                converter
        );
        ParagraphLayoutMetrics automatic = ParagraphLayoutMetrics.resolve(
                current,
                null,
                null,
                converter
        );

        assertEquals(0, contextual.getMarginTopPixels());
        assertEquals(10, automatic.getMarginTopPixels());
    }

    @Test
    public void exactAndAtLeastSpacingNeverClipLargestRun() {
        WordMeasurementConverter converter =
                new WordMeasurementConverter(144f, 1f, 1f);
        WordParagraph exact = paragraph(
                new WordParagraphStyle.Builder()
                        .setLineSpacingValue(360)
                        .setLineRule(WordParagraphStyle.LineRule.EXACT)
                        .build(),
                12f,
                "exact"
        );
        ParagraphLayoutMetrics metrics = ParagraphLayoutMetrics.resolve(
                exact,
                null,
                null,
                converter
        );

        assertTrue(metrics.getMinimumHeightPixels() >= 29);
        assertTrue(metrics.getLineSpacingExtraPixels() > 0f);
        assertEquals(1f, metrics.getLineSpacingMultiplier(), DELTA);
    }

    @Test
    public void fontScaleIsAppliedOnceAndEmptyParagraphKeepsOneSafeLine() {
        WordParagraph empty = paragraph(
                WordParagraphStyle.defaults(),
                12f,
                ""
        );
        ParagraphLayoutMetrics normal = ParagraphLayoutMetrics.resolve(
                empty,
                null,
                null,
                new WordMeasurementConverter(144f, 1f, 1f)
        );
        ParagraphLayoutMetrics enlarged = ParagraphLayoutMetrics.resolve(
                empty,
                null,
                null,
                new WordMeasurementConverter(144f, 1f, 1.5f)
        );

        assertTrue(normal.getMinimumHeightPixels() > 0);
        assertEquals(
                1.5f,
                enlarged.getMinimumHeightPixels()
                        / (float) normal.getMinimumHeightPixels(),
                0.1f
        );
    }

    @Test
    public void characterIndentFallbackUsesHundredthsOfCharacter() {
        WordParagraph paragraph = paragraph(
                new WordParagraphStyle.Builder()
                        .setStartIndentCharacters(200)
                        .setEndIndentCharacters(100)
                        .build(),
                10f,
                "chars"
        );
        ParagraphLayoutMetrics metrics = ParagraphLayoutMetrics.resolve(
                paragraph,
                null,
                null,
                new WordMeasurementConverter(144f, 1f, 1f)
        );

        assertEquals(20, metrics.getStartIndentPixels());
        assertEquals(10, metrics.getEndIndentPixels());
    }

    @Test
    public void hangingAndFirstLineReplaceEachOtherInsteadOfAddingSigns() {
        WordParagraphStyle inherited = new WordParagraphStyle.Builder()
                .setFirstLineIndentTwips(360)
                .build();
        WordParagraphStyle direct = new WordParagraphStyle.Builder()
                .setHangingIndentTwips(240)
                .build();
        WordParagraphStyle resolved = WordParagraphStyle.merge(inherited, direct);

        assertTrue(!resolved.hasFirstLineIndent());
        assertTrue(resolved.hasHangingIndent());
        assertEquals(240, resolved.getHangingIndentTwips());
    }

    private static WordParagraph paragraph(
            WordParagraphStyle paragraphStyle,
            float fontPoints,
            String text
    ) {
        WordRunStyle runStyle = new WordRunStyle.Builder()
                .setFontSizePoints(fontPoints)
                .build();
        return new WordParagraph(
                text.isEmpty()
                        ? Collections.emptyList()
                        : Collections.singletonList(new WordRun(text, runStyle, null)),
                paragraphStyle,
                runStyle,
                "",
                WordParagraph.Role.BODY
        );
    }
}
