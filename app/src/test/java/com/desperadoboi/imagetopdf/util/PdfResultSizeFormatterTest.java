package com.desperadoboi.imagetopdf.util;

import android.net.FakeUri;

import com.desperadoboi.imagetopdf.model.PdfResult;

import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.assertEquals;

public class PdfResultSizeFormatterTest {
    @Test
    public void knownPositiveSizeIsFormatted() {
        assertEquals("1.5 KB", PdfResultSizeFormatter.format(
                result(1536L),
                Locale.US,
                "Размер неизвестен"
        ));
    }

    @Test
    public void unknownSizeUsesExplicitLabel() {
        assertEquals("Размер неизвестен", PdfResultSizeFormatter.format(
                result(PdfResult.UNKNOWN_SIZE),
                Locale.US,
                "Размер неизвестен"
        ));
    }

    @Test
    public void knownZeroByteFileIsNotTreatedAsUnknown() {
        assertEquals("0 B", PdfResultSizeFormatter.format(
                result(0L),
                Locale.US,
                "Размер неизвестен"
        ));
    }

    private PdfResult result(long sizeBytes) {
        return new PdfResult(
                FakeUri.create("content://test/result.pdf"),
                "result.pdf",
                sizeBytes,
                1
        );
    }
}
