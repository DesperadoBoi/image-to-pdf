package com.desperadoboi.imagetopdf.document;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DocumentLimitsTest {
    @Test
    public void appliesPerTypeLimits() {
        assertTrue(DocumentLimits.isAllowedKnownSize(DocumentLimits.MAX_PDF_BYTES, DocumentType.PDF));
        assertFalse(DocumentLimits.isAllowedKnownSize(
                DocumentLimits.MAX_PDF_BYTES + 1,
                DocumentType.PDF
        ));
        assertTrue(DocumentLimits.isAllowedKnownSize(
                DocumentLimits.MAX_TEXT_BYTES,
                DocumentType.TEXT
        ));
        assertFalse(DocumentLimits.isAllowedKnownSize(
                DocumentLimits.MAX_TEXT_BYTES + 1,
                DocumentType.CSV
        ));
        assertFalse(DocumentLimits.isAllowedKnownSize(-1, DocumentType.PNG));
    }
}
