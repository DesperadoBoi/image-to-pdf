package com.desperadoboi.imagetopdf.ui.watermark;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class PdfWatermarkFileNameTest {
    @Test public void safeSourceStemIsPreserved() {
        assertEquals("Quarterly_Report_watermark.pdf",
                PdfWatermarkFileName.suggest("Quarterly Report.pdf", "2026-08-24"));
    }

    @Test public void cyrillicSourceStemIsSupported() {
        assertEquals("Договор_1_watermark.pdf",
                PdfWatermarkFileName.suggest("Договор 1.pdf", "2026-08-24"));
    }

    @Test public void unsafeOnlySourceFallsBackToDate() {
        assertEquals("Watermark_2026-08-24.pdf",
                PdfWatermarkFileName.suggest("<>:\\/?*.pdf", "2026-08-24"));
    }

    @Test public void outputNeverContainsPathSeparators() {
        String name = PdfWatermarkFileName.suggest("../../private/report.pdf", "2026-08-24");
        assertFalse(name.contains("/"));
        assertFalse(name.contains("\\"));
        assertTrue(name.endsWith("_watermark.pdf"));
    }
}
