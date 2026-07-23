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
        assertTrue(DocumentLimits.isAllowedKnownSize(
                DocumentLimits.MAX_XLSX_BYTES,
                DocumentType.XLSX
        ));
        assertFalse(DocumentLimits.isAllowedKnownSize(
                DocumentLimits.MAX_XLSX_BYTES + 1,
                DocumentType.XLSX
        ));
        assertTrue(DocumentLimits.isAllowedKnownSize(
                DocumentLimits.MAX_DOCX_BYTES,
                DocumentType.DOCX
        ));
        assertFalse(DocumentLimits.isAllowedKnownSize(
                DocumentLimits.MAX_DOCX_BYTES + 1,
                DocumentType.DOCX
        ));
        assertFalse(DocumentLimits.isAllowedKnownSize(-1, DocumentType.PNG));
    }
}
