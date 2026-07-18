package com.desperadoboi.imagetopdf.pdf;

import com.desperadoboi.imagetopdf.model.ImagePlacementMode;
import com.desperadoboi.imagetopdf.model.MarginPreset;
import com.desperadoboi.imagetopdf.model.PageSizeMode;
import com.desperadoboi.imagetopdf.model.PdfOptions;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class PdfPageLayoutCalculatorTest {
    private static final float DELTA = 0.0001f;

    @Test
    public void a4AlwaysUsesFixedPortraitPage() {
        PdfPageLayout layout = calculate(PageSizeMode.A4, MarginPreset.STANDARD, 3000, 2000);

        assertEquals(595, layout.getPageWidth());
        assertEquals(842, layout.getPageHeight());
    }

    @Test
    public void a4ContentAreaUsesSelectedMargins() {
        assertA4Margins(MarginPreset.NONE, 0f, 595f, 842f);
        assertA4Margins(MarginPreset.SMALL, 12f, 571f, 818f);
        assertA4Margins(MarginPreset.STANDARD, 24f, 547f, 794f);
    }

    @Test
    public void imagePortraitPreservesAspectRatio() {
        PdfPageLayout layout = calculate(PageSizeMode.IMAGE, MarginPreset.NONE, 400, 800);

        assertEquals(421f, layout.getContentWidth(), DELTA);
        assertEquals(842f, layout.getContentHeight(), DELTA);
        assertAspectRatio(layout, 400f / 800f);
    }

    @Test
    public void imageLandscapePreservesAspectRatio() {
        PdfPageLayout layout = calculate(PageSizeMode.IMAGE, MarginPreset.NONE, 800, 400);

        assertEquals(842f, layout.getContentWidth(), DELTA);
        assertEquals(421f, layout.getContentHeight(), DELTA);
        assertAspectRatio(layout, 800f / 400f);
    }

    @Test
    public void imageSquareUsesSquarePage() {
        PdfPageLayout layout = calculate(PageSizeMode.IMAGE, MarginPreset.NONE, 500, 500);

        assertEquals(842, layout.getPageWidth());
        assertEquals(842, layout.getPageHeight());
        assertEquals(842f, layout.getContentWidth(), DELTA);
        assertEquals(842f, layout.getContentHeight(), DELTA);
    }

    @Test
    public void imageWithManualRotationUsesSwappedDimensions() {
        PdfPageLayout layout = calculate(PageSizeMode.IMAGE, MarginPreset.NONE, 800, 400);

        assertEquals(842, layout.getPageWidth());
        assertEquals(421, layout.getPageHeight());
    }

    @Test
    public void imageLongPageSideEquals842ForEveryMargin() {
        assertLongSide(calculate(PageSizeMode.IMAGE, MarginPreset.NONE, 800, 400));
        assertLongSide(calculate(PageSizeMode.IMAGE, MarginPreset.SMALL, 800, 400));
        assertLongSide(calculate(PageSizeMode.IMAGE, MarginPreset.STANDARD, 800, 400));
    }

    @Test
    public void imageMarginsAreIncludedInPageSize() {
        PdfPageLayout layout = calculate(PageSizeMode.IMAGE, MarginPreset.STANDARD, 400, 800);

        assertEquals(445, layout.getPageWidth());
        assertEquals(842, layout.getPageHeight());
        assertEquals(24f, layout.getContentLeft(), DELTA);
        assertEquals(24f, layout.getContentTop(), DELTA);
        assertEquals(421f, layout.getContentRight(), DELTA);
        assertEquals(818f, layout.getContentBottom(), DELTA);
    }

    @Test
    public void contentAreaIsAlwaysPositive() {
        PdfPageLayout layout = calculate(PageSizeMode.IMAGE, MarginPreset.STANDARD, 1, 1000);

        assertTrue(layout.getContentWidth() > 0f);
        assertTrue(layout.getContentHeight() > 0f);
    }

    @Test
    public void invalidImageDimensionsAreRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> calculate(PageSizeMode.IMAGE, MarginPreset.NONE, 0, 100)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> calculate(PageSizeMode.IMAGE, MarginPreset.NONE, 100, -1)
        );
    }

    private PdfPageLayout calculate(
            PageSizeMode pageSizeMode,
            MarginPreset marginPreset,
            int imageWidth,
            int imageHeight
    ) {
        return PdfPageLayoutCalculator.calculate(
                new PdfOptions(pageSizeMode, ImagePlacementMode.FIT, marginPreset),
                imageWidth,
                imageHeight
        );
    }

    private void assertA4Margins(
            MarginPreset marginPreset,
            float expectedMargin,
            float expectedContentWidth,
            float expectedContentHeight
    ) {
        PdfPageLayout layout = calculate(PageSizeMode.A4, marginPreset, 300, 500);

        assertEquals(expectedMargin, layout.getContentLeft(), DELTA);
        assertEquals(expectedMargin, layout.getContentTop(), DELTA);
        assertEquals(expectedContentWidth, layout.getContentWidth(), DELTA);
        assertEquals(expectedContentHeight, layout.getContentHeight(), DELTA);
    }

    private void assertAspectRatio(PdfPageLayout layout, float expectedAspectRatio) {
        assertEquals(expectedAspectRatio, layout.getContentWidth() / layout.getContentHeight(), DELTA);
    }

    private void assertLongSide(PdfPageLayout layout) {
        assertEquals(
                842,
                Math.max(layout.getPageWidth(), layout.getPageHeight())
        );
    }
}
