package com.desperadoboi.imagetopdf.ui.watermark;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class PdfWatermarkOptionsTest {
    @Test public void defaultsMatchProductContract() {
        PdfWatermarkOptions options = PdfWatermarkOptions.defaults("КОПИЯ");
        assertEquals("КОПИЯ", options.getText());
        assertEquals(WatermarkStyle.SINGLE, options.getStyle());
        assertEquals(WatermarkPosition.CENTER, options.getPosition());
        assertEquals(0.30f, options.getOpacity(), 0.0001f);
        assertEquals(48f, options.getSizePoints(), 0.0001f);
        assertEquals(-45f, options.getRotationDegrees(), 0.0001f);
        assertEquals(WatermarkColor.GRAY, options.getColor());
    }

    @Test public void supportsCyrillicLatinDigitsSpacesAndPunctuation() {
        String value = "Копия / COPY № 12 (draft).";
        assertEquals(value, PdfWatermarkOptions.defaults(value).getText());
    }

    @Test public void blankTextIsInvalid() {
        assertFalse(PdfWatermarkOptions.defaults(" \n ").hasValidText());
    }

    @Test public void nonBlankTextIsValid() {
        assertTrue(PdfWatermarkOptions.defaults("COPY").hasValidText());
    }

    @Test public void acceptsSixtyUnicodeCodePoints() {
        assertEquals(60, PdfWatermarkOptions.defaults(repeat("Я", 60))
                .getText().codePointCount(0, 60));
    }

    @Test public void rejectsTextAboveLimit() {
        assertThrows(IllegalArgumentException.class,
                () -> PdfWatermarkOptions.defaults(repeat("A", 61)));
    }

    @Test public void opacityRangeIsEnforced() {
        PdfWatermarkOptions defaults = PdfWatermarkOptions.defaults("COPY");
        assertEquals(0.10f, defaults.withOpacity(0.10f).getOpacity(), 0f);
        assertEquals(0.80f, defaults.withOpacity(0.80f).getOpacity(), 0f);
        assertThrows(IllegalArgumentException.class, () -> defaults.withOpacity(0.09f));
        assertThrows(IllegalArgumentException.class, () -> defaults.withOpacity(0.81f));
    }

    @Test public void sizeRangeIsEnforced() {
        PdfWatermarkOptions defaults = PdfWatermarkOptions.defaults("COPY");
        assertEquals(18f, defaults.withSizePoints(18f).getSizePoints(), 0f);
        assertEquals(96f, defaults.withSizePoints(96f).getSizePoints(), 0f);
        assertThrows(IllegalArgumentException.class, () -> defaults.withSizePoints(17f));
        assertThrows(IllegalArgumentException.class, () -> defaults.withSizePoints(97f));
    }

    @Test public void rotationRangeIsEnforced() {
        PdfWatermarkOptions defaults = PdfWatermarkOptions.defaults("COPY");
        assertEquals(-60f, defaults.withRotationDegrees(-60f).getRotationDegrees(), 0f);
        assertEquals(60f, defaults.withRotationDegrees(60f).getRotationDegrees(), 0f);
        assertThrows(IllegalArgumentException.class,
                () -> defaults.withRotationDegrees(-61f));
        assertThrows(IllegalArgumentException.class,
                () -> defaults.withRotationDegrees(61f));
    }

    @Test public void immutableUpdatesPreserveOtherFields() {
        PdfWatermarkOptions options = PdfWatermarkOptions.defaults("COPY")
                .withStyle(WatermarkStyle.TILED)
                .withPosition(WatermarkPosition.BOTTOM_RIGHT)
                .withColor(WatermarkColor.RED);
        assertEquals(WatermarkStyle.TILED, options.getStyle());
        assertEquals(WatermarkPosition.BOTTOM_RIGHT, options.getPosition());
        assertEquals(WatermarkColor.RED, options.getColor());
        assertEquals("COPY", options.getText());
    }

    private static String repeat(String value, int count) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < count; index++) result.append(value);
        return result.toString();
    }
}
