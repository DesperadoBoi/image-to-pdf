package com.desperadoboi.imagetopdf.ui.idcard;

import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class IdCardExportOptionsTest {
    @Test public void watermarkIsOffByDefault() {
        IdCardExportOptions options = IdCardExportOptions.defaults(Locale.ENGLISH);

        assertFalse(options.isWatermarkEnabled());
        assertEquals(IdCardExportPreset.EASY_TO_READ, options.getPreset());
        assertTrue(options.isValid());
    }

    @Test public void defaultEnglishWatermarkIsCopy() {
        assertEquals("COPY", IdCardExportOptions.defaultWatermarkText(Locale.ENGLISH));
    }

    @Test public void defaultRussianWatermarkIsLocalized() {
        assertEquals("КОПИЯ", IdCardExportOptions.defaultWatermarkText(new Locale("ru")));
    }

    @Test public void customCyrillicWatermarkIsPreserved() {
        IdCardExportOptions options = IdCardExportOptions.defaults(Locale.ENGLISH)
                .withWatermarkEnabled(true)
                .withWatermarkText("АРХИВНАЯ КОПИЯ");

        assertEquals("АРХИВНАЯ КОПИЯ", options.getWatermarkText());
        assertTrue(options.isValid());
    }

    @Test public void trailingSpaceIsPreservedWhileTypingMultipleWords() {
        IdCardExportOptions options = IdCardExportOptions.defaults(Locale.ENGLISH)
                .withWatermarkText("ARCHIVE ");

        assertEquals("ARCHIVE ", options.getWatermarkText());
    }

    @Test public void emptyEnabledWatermarkIsInvalid() {
        IdCardExportOptions options = new IdCardExportOptions(
                IdCardExportPreset.EASY_TO_READ,
                true,
                "   "
        );

        assertFalse(options.isValid());
    }

    @Test public void emptyDisabledWatermarkIsAllowed() {
        IdCardExportOptions options = new IdCardExportOptions(
                IdCardExportPreset.EASY_TO_READ,
                false,
                ""
        );

        assertTrue(options.isValid());
    }

    @Test public void watermarkLengthLimitIsAcceptedAtBoundary() {
        String text = repeat('A', IdCardExportOptions.MAX_WATERMARK_LENGTH);

        assertEquals(
                text,
                new IdCardExportOptions(IdCardExportPreset.ACTUAL_SIZE, true, text)
                        .getWatermarkText()
        );
    }

    @Test public void watermarkOverLimitIsRejected() {
        String text = repeat('A', IdCardExportOptions.MAX_WATERMARK_LENGTH + 1);

        assertThrows(
                IllegalArgumentException.class,
                () -> new IdCardExportOptions(IdCardExportPreset.EASY_TO_READ, true, text)
        );
    }

    @Test public void watermarkTextCannotAffectPreset() {
        IdCardExportOptions options = new IdCardExportOptions(
                IdCardExportPreset.ACTUAL_SIZE,
                true,
                "COPY_2026.pdf"
        );

        assertEquals(IdCardExportPreset.ACTUAL_SIZE, options.getPreset());
    }

    private static String repeat(char value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int index = 0; index < count; index++) result.append(value);
        return result.toString();
    }
}
