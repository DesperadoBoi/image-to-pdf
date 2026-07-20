package com.desperadoboi.imagetopdf.image;

import com.desperadoboi.imagetopdf.model.CropRect;
import com.desperadoboi.imagetopdf.model.NormalizedPoint;
import com.desperadoboi.imagetopdf.model.PageEditSpec;
import com.desperadoboi.imagetopdf.model.PerspectiveQuad;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SourceResolutionCalculatorTest {
    @Test
    public void fullImageUsesOnlyResolutionNeededForFitTarget() {
        assertDimensions(
                1000,
                750,
                fit(PageEditSpec.DEFAULT, PageProcessingMode.FINAL, 1000, 1000)
        );
    }

    @Test
    public void narrowCropRequiresMoreSourceResolution() {
        PageEditSpec cropped = PageEditSpec.DEFAULT.withCropRect(
                new CropRect(0.25f, 0.25f, 0.75f, 0.75f)
        );

        assertDimensions(2000, 1500, fit(cropped, PageProcessingMode.FINAL, 1000, 1000));
    }

    @Test
    public void smallPerspectiveQuadRequiresMoreSourceResolution() {
        PageEditSpec perspective = PageEditSpec.DEFAULT.withPerspectiveQuad(centerQuad());

        assertDimensions(
                2000,
                1500,
                fit(perspective, PageProcessingMode.FINAL, 1000, 1000)
        );
    }

    @Test
    public void combinedPerspectiveAndCropCanRequireFullSource() {
        PageEditSpec spec = PageEditSpec.DEFAULT
                .withPerspectiveQuad(centerQuad())
                .withCropRect(new CropRect(0.25f, 0.25f, 0.75f, 0.75f));

        assertDimensions(4000, 3000, fit(spec, PageProcessingMode.FINAL, 1000, 1000));
    }

    @Test
    public void editorModesIgnoreTransformsThatAreNotDisplayed() {
        PageEditSpec spec = PageEditSpec.DEFAULT
                .withPerspectiveQuad(centerQuad())
                .withCropRect(new CropRect(0.25f, 0.25f, 0.75f, 0.75f));

        assertDimensions(
                1000,
                750,
                fit(spec, PageProcessingMode.ORIENTED_ONLY, 1000, 1000)
        );
        assertDimensions(
                2000,
                1500,
                fit(spec, PageProcessingMode.BEFORE_CROP, 1000, 1000)
        );
    }

    @Test
    public void outputTargetUsesBothRequiredDimensions() {
        EditedImageGeometryCalculator.Dimensions dimensions =
                SourceResolutionCalculator.calculateForOutputTarget(
                        4000,
                        3000,
                        PageEditSpec.DEFAULT,
                        PageProcessingMode.FINAL,
                        1200,
                        1000
                );

        assertDimensions(1334, 1000, dimensions);
    }

    @Test
    public void smallSourceIsNeverUpscaled() {
        EditedImageGeometryCalculator.Dimensions dimensions =
                SourceResolutionCalculator.calculateForOutputTarget(
                        400,
                        300,
                        PageEditSpec.DEFAULT,
                        PageProcessingMode.FINAL,
                        2000,
                        2000
                );

        assertDimensions(400, 300, dimensions);
    }

    private EditedImageGeometryCalculator.Dimensions fit(
            PageEditSpec spec,
            PageProcessingMode mode,
            int targetWidth,
            int targetHeight
    ) {
        return SourceResolutionCalculator.calculateForFitTarget(
                4000,
                3000,
                spec,
                mode,
                targetWidth,
                targetHeight
        );
    }

    private PerspectiveQuad centerQuad() {
        return new PerspectiveQuad(
                new NormalizedPoint(0.25f, 0.25f),
                new NormalizedPoint(0.75f, 0.25f),
                new NormalizedPoint(0.75f, 0.75f),
                new NormalizedPoint(0.25f, 0.75f)
        );
    }

    private void assertDimensions(
            int expectedWidth,
            int expectedHeight,
            EditedImageGeometryCalculator.Dimensions dimensions
    ) {
        assertEquals(expectedWidth, dimensions.getWidth());
        assertEquals(expectedHeight, dimensions.getHeight());
    }
}
