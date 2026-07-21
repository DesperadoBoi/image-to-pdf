package com.desperadoboi.imagetopdf.util;

import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class FileSizeFormatterTest {
    @Test
    public void zeroBytesFormatsAsBytes() {
        assertEquals("0 B", FileSizeFormatter.format(0L, Locale.US));
    }

    @Test
    public void bytesFormatWithoutFraction() {
        assertEquals("512 B", FileSizeFormatter.format(512L, Locale.US));
        assertEquals("1023 B", FileSizeFormatter.format(1023L, Locale.US));
    }

    @Test
    public void kilobytesUseOneFractionDigitAtMost() {
        assertEquals("1 KB", FileSizeFormatter.format(1024L, Locale.US));
        assertEquals("1.5 KB", FileSizeFormatter.format(1536L, Locale.US));
    }

    @Test
    public void megabytesUseOneFractionDigitAtMost() {
        assertEquals("1 MB", FileSizeFormatter.format(1024L * 1024L, Locale.US));
        assertEquals("1.5 MB", FileSizeFormatter.format(1536L * 1024L, Locale.US));
    }

    @Test
    public void boundaryValuesStayInExpectedUnits() {
        assertEquals("1023 B", FileSizeFormatter.format(1023L, Locale.US));
        assertEquals("1 KB", FileSizeFormatter.format(1024L, Locale.US));
        assertEquals("1023.9 KB", FileSizeFormatter.format(1024L * 1024L - 1L, Locale.US));
        assertEquals("1 MB", FileSizeFormatter.format(1024L * 1024L, Locale.US));
    }

    @Test
    public void localeControlsDecimalSeparator() {
        assertEquals("1,5 KB", FileSizeFormatter.format(1536L, Locale.GERMANY));
    }

    @Test
    public void invalidNegativeSizeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> FileSizeFormatter.format(-1L, Locale.US));
    }
}
