package com.desperadoboi.imagetopdf.model;

import com.desperadoboi.imagetopdf.pdf.RasterTarget;
import com.desperadoboi.imagetopdf.pdf.RasterTargetCalculator;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class PdfQualityProfileTest {
    @Test
    public void profilesExposeExpectedDpiInAscendingOrder() {
        assertEquals(96, PdfQualityProfile.COMPACT.getTargetDpi());
        assertEquals(144, PdfQualityProfile.BALANCED.getTargetDpi());
        assertEquals(216, PdfQualityProfile.HIGH.getTargetDpi());
        assertTrue(PdfQualityProfile.COMPACT.getTargetDpi()
                < PdfQualityProfile.BALANCED.getTargetDpi());
        assertTrue(PdfQualityProfile.BALANCED.getTargetDpi()
                < PdfQualityProfile.HIGH.getTargetDpi());
    }

    @Test
    public void unknownProfileCannotBeConstructed() {
        assertThrows(IllegalArgumentException.class, () -> PdfQualityProfile.valueOf("ULTRA"));
    }

    @Test
    public void smallSourceIsNeverEnlargedForAnyProfile() {
        for (PdfQualityProfile profile : PdfQualityProfile.values()) {
            RasterTarget target = RasterTargetCalculator.calculate(
                    320,
                    240,
                    0f,
                    0f,
                    595f,
                    842f,
                    ImagePlacementMode.FIT,
                    profile.getTargetDpi()
            );

            assertEquals(320, target.getTargetWidthPixels());
            assertEquals(240, target.getTargetHeightPixels());
        }
    }
}
