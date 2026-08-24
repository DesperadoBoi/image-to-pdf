package com.desperadoboi.imagetopdf.pdf;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class PdfWatermarkGeneratorTest {
    @Test public void letterPageTargetsOneHundredEightyDpi() {
        PdfWatermarkGenerator.RenderSize size = PdfWatermarkGenerator.calculateRenderSize(612, 792);
        assertEquals(1530, size.getWidth());
        assertEquals(1980, size.getHeight());
    }

    @Test public void landscapePreservesAspectRatio() {
        PdfWatermarkGenerator.RenderSize size = PdfWatermarkGenerator.calculateRenderSize(792, 612);
        assertEquals(1980, size.getWidth());
        assertEquals(1530, size.getHeight());
        assertEquals(792f / 612f, size.getWidth() / (float) size.getHeight(), 0.001f);
    }

    @Test public void veryLargePageIsBoundedByMemoryBudgetAndEdge() {
        PdfWatermarkGenerator.RenderSize size = PdfWatermarkGenerator.calculateRenderSize(12000, 10000);
        assertTrue(size.getWidth() <= PdfWatermarkGenerator.MAX_BITMAP_EDGE);
        assertTrue(size.getHeight() <= PdfWatermarkGenerator.MAX_BITMAP_EDGE);
        assertTrue(size.getPixels() <= PdfWatermarkGenerator.MAX_BITMAP_PIXELS);
    }

    @Test public void temporaryNameValidationRejectsTraversalAndProviderNames() {
        assertTrue(PdfWatermarkGenerator.isGeneratedTemporaryName(
                "watermark_pdf_550e8400-e29b-41d4-a716-446655440000.tmp"));
        assertFalse(PdfWatermarkGenerator.isGeneratedTemporaryName(
                "../watermark_pdf_550e8400-e29b-41d4-a716-446655440000.tmp"));
        assertFalse(PdfWatermarkGenerator.isGeneratedTemporaryName("private.pdf"));
    }
}
