package com.desperadoboi.imagetopdf.model;

import android.net.FakeUri;
import android.net.Uri;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class PdfResultTest {
    private static final Uri TEST_URI = FakeUri.create("content://test/result.pdf");

    @Test
    public void nullUriIsRejected() {
        assertThrows(
                NullPointerException.class,
                () -> new PdfResult(null, "result.pdf", PdfResult.UNKNOWN_SIZE_BYTES, 1)
        );
    }

    @Test
    public void emptyDisplayNameIsAllowedAndStored() {
        PdfResult result = new PdfResult(TEST_URI, "", PdfResult.UNKNOWN_SIZE_BYTES, 1);

        assertEquals("", result.getDisplayName());
    }

    @Test
    public void displayNameIsTrimmed() {
        PdfResult result = new PdfResult(TEST_URI, " result.pdf ", PdfResult.UNKNOWN_SIZE_BYTES, 1);

        assertEquals("result.pdf", result.getDisplayName());
    }

    @Test
    public void negativePageCountIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PdfResult(TEST_URI, "result.pdf", PdfResult.UNKNOWN_SIZE_BYTES, -1)
        );
    }

    @Test
    public void unknownSizeHasExplicitRepresentation() {
        PdfResult result = new PdfResult(
                TEST_URI,
                "result.pdf",
                PdfResult.UNKNOWN_SIZE_BYTES,
                1
        );

        assertEquals(-1L, PdfResult.UNKNOWN_SIZE_BYTES);
        assertEquals(PdfResult.UNKNOWN_SIZE_BYTES, result.getSizeBytes());
        assertFalse(result.hasKnownSize());
    }

    @Test
    public void nonNegativeSizeIsKnown() {
        PdfResult result = new PdfResult(TEST_URI, "result.pdf", 0L, 1);

        assertSame(TEST_URI, result.getUri());
        assertEquals(0L, result.getSizeBytes());
        assertTrue(result.hasKnownSize());
        assertEquals(1, result.getPageCount());
    }

    @Test
    public void negativeSizeExceptUnknownIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PdfResult(TEST_URI, "result.pdf", -2L, 1)
        );
    }
}
