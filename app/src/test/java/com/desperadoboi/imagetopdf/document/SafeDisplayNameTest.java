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
}
