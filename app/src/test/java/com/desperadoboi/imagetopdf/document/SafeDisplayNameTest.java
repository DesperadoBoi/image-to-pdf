package com.desperadoboi.imagetopdf.document;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SafeDisplayNameTest {
    @Test
    public void keepsNormalAndUnicodeNames() {
        assertEquals("report.pdf", SafeDisplayName.sanitize("report.pdf"));
        assertEquals("Таблица.csv", SafeDisplayName.sanitize("Таблица.csv"));
    }

    @Test
    public void removesPathTraversalAndSeparators() {
        assertEquals("secret.pdf", SafeDisplayName.sanitize("../../secret.pdf"));
        assertEquals("secret.pdf", SafeDisplayName.sanitize("..\\..\\secret.pdf"));
        assertFalse(SafeDisplayName.sanitize("folder/file.txt").contains("/"));
    }

    @Test
    public void replacesEmptyAndDotNames() {
        assertEquals("document", SafeDisplayName.sanitize(null));
        assertEquals("document", SafeDisplayName.sanitize(""));
        assertEquals("document", SafeDisplayName.sanitize(".."));
    }

    @Test
    public void boundsLongNameAndPreservesExtension() {
        String safe = SafeDisplayName.sanitize("я".repeat(200) + ".pdf");
        assertTrue(safe.codePointCount(0, safe.length()) <= SafeDisplayName.MAX_CODE_POINTS);
        assertTrue(safe.endsWith(".pdf"));
    }

    @Test
    public void providerDisplayNameHasPriority() {
        assertEquals("provider.xlsx", SafeDisplayName.resolve(
                "provider.xlsx",
                "stored.xlsx",
                "uri.xlsx",
                "Документ"
        ));
    }

    @Test
    public void storedMetadataPrecedesUriName() {
        assertEquals("stored.xlsx", SafeDisplayName.resolve(
                null,
                "stored.xlsx",
                "uri.xlsx",
                "Документ"
        ));
    }

    @Test
    public void internalProviderIdsAreNeverDisplayed() {
        assertEquals("Документ", SafeDisplayName.resolve(
                "msf:1000009229",
                null,
                "document:12345",
                "Документ"
        ));
        assertFalse(SafeDisplayName.resolve(
                null,
                null,
                "msf:1000009229",
                "Документ"
        ).contains("msf:"));
    }

    @Test
    public void cacheFileNamesAreNeverDisplayed() {
        assertEquals("Документ", SafeDisplayName.resolve(
                "viewer_06f4d12d-1f97-4a9a-860a-018b8d148462.cache",
                null,
                null,
                "Документ"
        ));
    }

    @Test
    public void uriNameIsSafelyDecodedAndSeparatorsAreRemoved() {
        assertEquals("report one.xlsx", SafeDisplayName.resolve(
                null,
                null,
                "folder%2Freport%20one.xlsx",
                "Документ"
        ));
    }

    @Test
    public void usesLocalizedFallback() {
        assertEquals("Документ", SafeDisplayName.resolve(
                null,
                null,
                null,
                "Документ"
        ));
    }
}
