package com.desperadoboi.imagetopdf.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class PdfOptionsTest {
    @Test
    public void defaultsUseA4FitAndStandardMargins() {
        PdfOptions options = PdfOptions.defaults();

        assertEquals(PageSizeMode.A4, options.getPageSizeMode());
        assertEquals(ImagePlacementMode.FIT, options.getImagePlacementMode());
        assertEquals(MarginPreset.STANDARD, options.getMarginPreset());
        assertEquals(PdfQualityProfile.BALANCED, options.getQualityProfile());
        assertEquals(PdfOrientationMode.AUTO, options.getOrientationMode());
    }

    @Test
    public void constructorKeepsEverySelectedEnum() {
        PdfOptions options = new PdfOptions(
                PageSizeMode.IMAGE,
                ImagePlacementMode.FILL,
                MarginPreset.SMALL,
                PdfQualityProfile.HIGH,
                PdfOrientationMode.LANDSCAPE
        );

        assertEquals(PageSizeMode.IMAGE, options.getPageSizeMode());
        assertEquals(ImagePlacementMode.FILL, options.getImagePlacementMode());
        assertEquals(MarginPreset.SMALL, options.getMarginPreset());
        assertEquals(PdfQualityProfile.HIGH, options.getQualityProfile());
        assertEquals(PdfOrientationMode.LANDSCAPE, options.getOrientationMode());
    }

    @Test
    public void nullValuesAreRejected() {
        assertThrows(
                NullPointerException.class,
                () -> new PdfOptions(null, ImagePlacementMode.FIT, MarginPreset.STANDARD)
        );
        assertThrows(
                NullPointerException.class,
                () -> new PdfOptions(PageSizeMode.A4, null, MarginPreset.STANDARD)
        );
        assertThrows(
                NullPointerException.class,
                () -> new PdfOptions(PageSizeMode.A4, ImagePlacementMode.FIT, null)
        );
    }

    @Test
    public void marginPresetsReturnPointValues() {
        assertEquals(0, MarginPreset.NONE.getMarginPoints());
        assertEquals(12, MarginPreset.SMALL.getMarginPoints());
        assertEquals(24, MarginPreset.STANDARD.getMarginPoints());
    }
}
