package com.desperadoboi.imagetopdf.image;

import com.desperadoboi.imagetopdf.model.CropRect;
import com.desperadoboi.imagetopdf.model.NormalizedPoint;
import com.desperadoboi.imagetopdf.model.PageEditSpec;
import com.desperadoboi.imagetopdf.model.PerspectiveQuad;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EditedImageGeometryCalculatorTest {
    @Test
    public void finalGeometryAppliesPerspectiveBeforeCrop() {
        PageEditSpec spec = PageEditSpec.DEFAULT
                .withPerspectiveQuad(centerQuad())
                .withCropRect(new CropRect(0.1f, 0.1f, 0.9f, 0.9f));

        EditedImageGeometryCalculator.Dimensions dimensions =
                EditedImageGeometryCalculator.calculate(
                        4000,
                        3000,
                        spec,
                        PageProcessingMode.FINAL
                );

        assertEquals(1600, dimensions.getWidth());
        assertEquals(1200, dimensions.getHeight());
    }

    @Test
    public void beforeCropKeepsWholeRectifiedOutput() {
        PageEditSpec spec = PageEditSpec.DEFAULT
                .withPerspectiveQuad(centerQuad())
                .withCropRect(new CropRect(0.1f, 0.1f, 0.9f, 0.9f));

        EditedImageGeometryCalculator.Dimensions dimensions =
                EditedImageGeometryCalculator.calculate(
                        4000,
                        3000,
                        spec,
                        PageProcessingMode.BEFORE_CROP
                );

        assertEquals(2000, dimensions.getWidth());
        assertEquals(1500, dimensions.getHeight());
    }

    @Test
    public void documentModeKeepsOrientedSourceGeometry() {
        PageEditSpec spec = PageEditSpec.DEFAULT.withPerspectiveQuad(centerQuad());

        EditedImageGeometryCalculator.Dimensions dimensions =
                EditedImageGeometryCalculator.calculate(
                        4000,
                        3000,
                        spec,
                        PageProcessingMode.ORIENTED_ONLY
                );

        assertEquals(4000, dimensions.getWidth());
        assertEquals(3000, dimensions.getHeight());
    }

    @Test
    public void extremeValidGeometryRemainsPositive() {
        EditedImageGeometryCalculator.Dimensions dimensions =
                EditedImageGeometryCalculator.calculate(
                        Integer.MAX_VALUE,
                        Integer.MAX_VALUE,
                        PageEditSpec.DEFAULT,
                        PageProcessingMode.FINAL
                );

        assertTrue(dimensions.getWidth() > 0);
        assertTrue(dimensions.getHeight() > 0);
    }

    private PerspectiveQuad centerQuad() {
        return new PerspectiveQuad(
                new NormalizedPoint(0.25f, 0.25f),
                new NormalizedPoint(0.75f, 0.25f),
                new NormalizedPoint(0.75f, 0.75f),
                new NormalizedPoint(0.25f, 0.75f)
        );
    }
}
