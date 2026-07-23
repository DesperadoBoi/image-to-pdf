package com.desperadoboi.imagetopdf.document.word;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class WordMeasurementConverterTest {
    private static final float DELTA = 0.001f;

    @Test
    public void convertsCanonicalFontHalfPointsToPoints() {
        assertEquals(8f, WordMeasurementConverter.halfPointsToPoints(16), DELTA);
        assertEquals(9f, WordMeasurementConverter.halfPointsToPoints(18), DELTA);
        assertEquals(10f, WordMeasurementConverter.halfPointsToPoints(20), DELTA);
        assertEquals(11f, WordMeasurementConverter.halfPointsToPoints(22), DELTA);
        assertEquals(12f, WordMeasurementConverter.halfPointsToPoints(24), DELTA);
        assertEquals(14f, WordMeasurementConverter.halfPointsToPoints(28), DELTA);
        assertEquals(16f, WordMeasurementConverter.halfPointsToPoints(32), DELTA);
        assertEquals(24f, WordMeasurementConverter.halfPointsToPoints(48), DELTA);
    }

    @Test
    public void rejectsOnlyCorruptedFontSizes() {
        assertNull(WordMeasurementConverter.safeFontPointsFromHalfPoints(-2));
        assertNull(WordMeasurementConverter.safeFontPointsFromHalfPoints(0));
        assertNull(WordMeasurementConverter.safeFontPointsFromHalfPoints(20_000));
        assertEquals(
                11f,
                WordMeasurementConverter.safeFontPointsFromHalfPoints(22),
                DELTA
        );
    }

    @Test
    public void convertsPhysicalWordGeometryAndAppliesFontScaleOnce() {
        WordMeasurementConverter converter =
                new WordMeasurementConverter(144f, 2f, 1.5f);

        assertEquals(2f, converter.pointsToPhysicalPixels(1f), DELTA);
        assertEquals(3f, converter.fontPointsToPixels(1f), DELTA);
        assertEquals(72f, converter.twipsToPixels(720f), DELTA);
        assertEquals(144f, converter.twipsToPixels(1_440f), DELTA);
        assertEquals(144f, converter.emuToPixels(914_400L), DELTA);
        assertEquals(72f, converter.emuToPixels(457_200L), DELTA);
        assertEquals(1f, converter.eighthPointsToPixels(4), DELTA);
    }

    @Test
    public void supportsPercentTableAndLineSpacingUnits() {
        WordMeasurementConverter converter =
                new WordMeasurementConverter(144f, 1f, 1f);

        assertEquals(
                0.5f,
                WordMeasurementConverter.percentageToFraction(2_500),
                DELTA
        );
        assertEquals(
                300f,
                converter.tableWidthToPixels(
                        new WordTableWidth(WordTableWidth.Type.PERCENT, 2_500),
                        600f
                ),
                DELTA
        );
        assertEquals(
                144f,
                converter.tableWidthToPixels(
                        new WordTableWidth(WordTableWidth.Type.DXA, 1_440),
                        600f
                ),
                DELTA
        );
        assertEquals(
                1.5f,
                WordMeasurementConverter.autoLineSpacingMultiplier(360),
                DELTA
        );
    }

    @Test
    public void fallsBackToLogicalDpiWhenPhysicalDpiIsInvalid() {
        WordMeasurementConverter converter =
                new WordMeasurementConverter(0f, 2f, 1f);
        assertEquals(320f, converter.emuToPixels(914_400L), DELTA);
    }

    @Test
    public void supportsAccessibilityFontScalesWithoutChangingGeometry() {
        float[] scales = {1f, 1.15f, 1.3f, 1.5f};
        for (float scale : scales) {
            WordMeasurementConverter converter =
                    new WordMeasurementConverter(144f, 1f, scale);
            assertEquals(20f * scale, converter.fontPointsToPixels(10f), DELTA);
            assertEquals(144f, converter.twipsToPixels(1_440f), DELTA);
        }
    }

    @Test
    public void mapsCommonWordFontFamiliesWithoutTurningSansIntoSerif() {
        assertEquals(
                "sans-serif",
                new WordRunStyle.Builder().setFontFamily("sans-serif")
                        .build().getFontFamily()
        );
        assertEquals(
                "sans-serif",
                new WordRunStyle.Builder().setFontFamily("Calibri")
                        .build().getFontFamily()
        );
        assertEquals(
                "serif",
                new WordRunStyle.Builder().setFontFamily("Cambria")
                        .build().getFontFamily()
        );
        assertEquals(
                "monospace",
                new WordRunStyle.Builder().setFontFamily("Consolas")
                        .build().getFontFamily()
        );
    }
}
