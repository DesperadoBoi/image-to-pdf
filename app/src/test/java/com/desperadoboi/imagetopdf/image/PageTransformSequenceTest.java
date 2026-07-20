package com.desperadoboi.imagetopdf.image;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class PageTransformSequenceTest {
    @Test
    public void finalOrderIsExifRotationPerspectiveCropScale() {
        assertEquals(
                Arrays.asList(
                        PageTransformSequence.Step.EXIF,
                        PageTransformSequence.Step.MANUAL_ROTATION,
                        PageTransformSequence.Step.PERSPECTIVE,
                        PageTransformSequence.Step.CROP,
                        PageTransformSequence.Step.SCALE
                ),
                PageTransformSequence.forMode(PageProcessingMode.FINAL)
        );
    }

    @Test
    public void cropToolOmitsAppliedCrop() {
        assertEquals(
                Arrays.asList(
                        PageTransformSequence.Step.EXIF,
                        PageTransformSequence.Step.MANUAL_ROTATION,
                        PageTransformSequence.Step.PERSPECTIVE,
                        PageTransformSequence.Step.SCALE
                ),
                PageTransformSequence.forMode(PageProcessingMode.BEFORE_CROP)
        );
    }

    @Test
    public void documentToolUsesOnlyOrientationBeforeScale() {
        assertEquals(
                Arrays.asList(
                        PageTransformSequence.Step.EXIF,
                        PageTransformSequence.Step.MANUAL_ROTATION,
                        PageTransformSequence.Step.SCALE
                ),
                PageTransformSequence.forMode(PageProcessingMode.ORIENTED_ONLY)
        );
    }
}
