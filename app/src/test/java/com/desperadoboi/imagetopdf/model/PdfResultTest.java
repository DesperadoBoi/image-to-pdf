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
                () -> new PdfResult(null, "result.pdf", 0L, 1)
        );
    }

    @Test
    public void emptyDisplayNameIsAllowedAndStored() {
        PdfResult result = new PdfResult(TEST_URI, "", 0L, 1);

        assertEquals("", result.getDisplayName());
    }

    @Test
    public void displayNameIsTrimmed() {
        PdfResult result = new PdfResult(TEST_URI, " result.pdf ", 0L, 1);

        assertEquals("result.pdf", result.getDisplayName());
    }

    @Test
    public void negativePageCountIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PdfResult(TEST_URI, "result.pdf", 0L, -1)
        );
    }

    @Test
    public void pendingMetadataUsesNonNegativeSizeAndMarksItUnknown() {
        PdfResult result = PdfResult.pendingMetadata(
                TEST_URI,
                "result.pdf",
                1,
                42L,
                "test.provider"
        );

        assertEquals(0L, result.getSizeBytes());
        assertFalse(result.hasKnownSize());
        assertEquals(42L, result.getTimestamp());
        assertEquals("test.provider", result.getLocationLabel());
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
    public void negativeSizeIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PdfResult(TEST_URI, "result.pdf", -2L, 1)
        );
    }

    @Test
    public void metadataFieldsAreRetainedAndTrimmed() {
        PdfResult result = new PdfResult(
                TEST_URI,
                " result.pdf ",
                2048L,
                3,
                1234L,
                " documents.provider "
        );

        assertEquals("result.pdf", result.getDisplayName());
        assertEquals(2048L, result.getSizeBytes());
        assertEquals(3, result.getPageCount());
        assertEquals(1234L, result.getTimestamp());
        assertEquals("documents.provider", result.getLocationLabel());
    }

    @Test
    public void knownMetadataUpdateKeepsIdentityAndPageCount() {
        PdfResult pending = PdfResult.pendingMetadata(
                TEST_URI,
                "fallback.pdf",
                4,
                1234L,
                "provider"
        );

        PdfResult result = pending.withKnownMetadata(
                " resolved.pdf ",
                4096L,
                " resolved.provider "
        );

        assertSame(TEST_URI, result.getUri());
        assertEquals("resolved.pdf", result.getDisplayName());
        assertEquals(4096L, result.getSizeBytes());
        assertTrue(result.hasKnownSize());
        assertEquals(4, result.getPageCount());
        assertEquals(1234L, result.getTimestamp());
        assertEquals("resolved.provider", result.getLocationLabel());
    }

    @Test
    public void negativeTimestampIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PdfResult(TEST_URI, "result.pdf", 1L, 1, -1L, "provider")
        );
    }
}
