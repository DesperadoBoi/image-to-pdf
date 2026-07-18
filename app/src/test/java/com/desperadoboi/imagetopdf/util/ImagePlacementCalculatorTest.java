package com.desperadoboi.imagetopdf.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ImagePlacementCalculatorTest {
    private static final float DELTA = 0.0001f;

    @Test
    public void portraitImageFitsWithinContainer() {
        ImagePlacementCalculator.PlacementRect rect =
                ImagePlacementCalculator.calculateCenteredFit(1200, 1800, 24f, 24f, 547f, 794f);

        assertEquals(529.3333f, rect.getWidth(), DELTA);
        assertEquals(794f, rect.getHeight(), DELTA);
        assertWithinBounds(rect, 24f, 24f, 547f, 794f);
        assertAspectRatioPreserved(rect, 1200f / 1800f);
    }

    @Test
    public void landscapeImageFitsWithinContainer() {
        ImagePlacementCalculator.PlacementRect rect =
                ImagePlacementCalculator.calculateCenteredFit(1800, 1200, 24f, 24f, 547f, 794f);

        assertEquals(547f, rect.getWidth(), DELTA);
        assertEquals(364.6667f, rect.getHeight(), DELTA);
        assertWithinBounds(rect, 24f, 24f, 547f, 794f);
        assertAspectRatioPreserved(rect, 1800f / 1200f);
    }

    @Test
    public void squareImageFitsWithinContainer() {
        ImagePlacementCalculator.PlacementRect rect =
                ImagePlacementCalculator.calculateCenteredFit(1500, 1500, 24f, 24f, 547f, 794f);

        assertEquals(547f, rect.getWidth(), DELTA);
        assertEquals(547f, rect.getHeight(), DELTA);
        assertWithinBounds(rect, 24f, 24f, 547f, 794f);
        assertAspectRatioPreserved(rect, 1f);
    }

    @Test
    public void invalidDimensionsAreRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ImagePlacementCalculator.calculateCenteredFit(0, 100, 0f, 0f, 100f, 100f)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> ImagePlacementCalculator.calculateCenteredFit(100, 100, 0f, 0f, 0f, 100f)
        );
    }

    private void assertWithinBounds(
            ImagePlacementCalculator.PlacementRect rect,
            float containerLeft,
            float containerTop,
            float containerWidth,
            float containerHeight
    ) {
        assertTrue(rect.getLeft() >= containerLeft - DELTA);
        assertTrue(rect.getTop() >= containerTop - DELTA);
        assertTrue(rect.getRight() <= containerLeft + containerWidth + DELTA);
        assertTrue(rect.getBottom() <= containerTop + containerHeight + DELTA);
    }

    private void assertAspectRatioPreserved(
            ImagePlacementCalculator.PlacementRect rect,
            float expectedAspectRatio
    ) {
        assertEquals(expectedAspectRatio, rect.getWidth() / rect.getHeight(), DELTA);
    }
}
