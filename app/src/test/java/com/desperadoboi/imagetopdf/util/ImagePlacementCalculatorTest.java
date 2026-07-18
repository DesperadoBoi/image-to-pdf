package com.desperadoboi.imagetopdf.util;

import com.desperadoboi.imagetopdf.model.ImagePlacementMode;

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

    @Test
    public void fitDrawPlanKeepsPortraitImageFullyVisible() {
        ImagePlacementCalculator.ImageDrawPlan plan = calculateFit(1200, 1800, 24f, 24f, 547f, 794f);

        assertFullSource(plan.getSourceRect(), 1200f, 1800f);
        assertWithinBounds(plan.getDestinationRect(), 24f, 24f, 547f, 794f);
        assertAspectRatioPreserved(plan.getDestinationRect(), 1200f / 1800f);
    }

    @Test
    public void fitDrawPlanKeepsLandscapeImageFullyVisible() {
        ImagePlacementCalculator.ImageDrawPlan plan = calculateFit(1800, 1200, 24f, 24f, 547f, 794f);

        assertFullSource(plan.getSourceRect(), 1800f, 1200f);
        assertWithinBounds(plan.getDestinationRect(), 24f, 24f, 547f, 794f);
        assertAspectRatioPreserved(plan.getDestinationRect(), 1800f / 1200f);
    }

    @Test
    public void fillDestinationCoversWholeContentArea() {
        ImagePlacementCalculator.ImageDrawPlan plan = calculateFill(2000, 1000, 24f, 24f, 547f, 794f);

        assertRect(plan.getDestinationRect(), 24f, 24f, 571f, 818f);
    }

    @Test
    public void fillWideImageCropsSidesSymmetrically() {
        ImagePlacementCalculator.ImageDrawPlan plan = calculateFill(2000, 1000, 0f, 0f, 500f, 500f);

        assertRect(plan.getSourceRect(), 500f, 0f, 1500f, 1000f);
        assertSourceInsideBitmap(plan.getSourceRect(), 2000f, 1000f);
        assertCenteredCrop(plan.getSourceRect(), 1000f, 500f);
    }

    @Test
    public void fillTallImageCropsTopAndBottomSymmetrically() {
        ImagePlacementCalculator.ImageDrawPlan plan = calculateFill(1000, 2000, 0f, 0f, 500f, 500f);

        assertRect(plan.getSourceRect(), 0f, 500f, 1000f, 1500f);
        assertSourceInsideBitmap(plan.getSourceRect(), 1000f, 2000f);
        assertCenteredCrop(plan.getSourceRect(), 500f, 1000f);
    }

    @Test
    public void fillSquareImageCoversRectangularArea() {
        ImagePlacementCalculator.ImageDrawPlan plan = calculateFill(1000, 1000, 0f, 0f, 500f, 250f);

        assertRect(plan.getSourceRect(), 0f, 250f, 1000f, 750f);
        assertRect(plan.getDestinationRect(), 0f, 0f, 500f, 250f);
        assertSourceInsideBitmap(plan.getSourceRect(), 1000f, 1000f);
    }

    @Test
    public void invalidDrawPlanInputIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ImagePlacementCalculator.calculateDrawPlan(
                        0,
                        100,
                        0f,
                        0f,
                        100f,
                        100f,
                        ImagePlacementMode.FIT
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> ImagePlacementCalculator.calculateDrawPlan(
                        100,
                        100,
                        -1f,
                        0f,
                        100f,
                        100f,
                        ImagePlacementMode.FIT
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> ImagePlacementCalculator.calculateDrawPlan(
                        100,
                        100,
                        0f,
                        0f,
                        0f,
                        100f,
                        ImagePlacementMode.FIT
                )
        );
        assertThrows(
                NullPointerException.class,
                () -> ImagePlacementCalculator.calculateDrawPlan(
                        100,
                        100,
                        0f,
                        0f,
                        100f,
                        100f,
                        null
                )
        );
    }

    private ImagePlacementCalculator.ImageDrawPlan calculateFit(
            int imageWidth,
            int imageHeight,
            float containerLeft,
            float containerTop,
            float containerWidth,
            float containerHeight
    ) {
        return ImagePlacementCalculator.calculateDrawPlan(
                imageWidth,
                imageHeight,
                containerLeft,
                containerTop,
                containerWidth,
                containerHeight,
                ImagePlacementMode.FIT
        );
    }

    private ImagePlacementCalculator.ImageDrawPlan calculateFill(
            int imageWidth,
            int imageHeight,
            float containerLeft,
            float containerTop,
            float containerWidth,
            float containerHeight
    ) {
        return ImagePlacementCalculator.calculateDrawPlan(
                imageWidth,
                imageHeight,
                containerLeft,
                containerTop,
                containerWidth,
                containerHeight,
                ImagePlacementMode.FILL
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

    private void assertFullSource(
            ImagePlacementCalculator.PlacementRect rect,
            float imageWidth,
            float imageHeight
    ) {
        assertRect(rect, 0f, 0f, imageWidth, imageHeight);
    }

    private void assertRect(
            ImagePlacementCalculator.PlacementRect rect,
            float left,
            float top,
            float right,
            float bottom
    ) {
        assertEquals(left, rect.getLeft(), DELTA);
        assertEquals(top, rect.getTop(), DELTA);
        assertEquals(right, rect.getRight(), DELTA);
        assertEquals(bottom, rect.getBottom(), DELTA);
    }

    private void assertSourceInsideBitmap(
            ImagePlacementCalculator.PlacementRect rect,
            float imageWidth,
            float imageHeight
    ) {
        assertTrue(rect.getLeft() >= 0f);
        assertTrue(rect.getTop() >= 0f);
        assertTrue(rect.getRight() <= imageWidth + DELTA);
        assertTrue(rect.getBottom() <= imageHeight + DELTA);
    }

    private void assertCenteredCrop(
            ImagePlacementCalculator.PlacementRect rect,
            float expectedCenterX,
            float expectedCenterY
    ) {
        assertEquals(expectedCenterX, (rect.getLeft() + rect.getRight()) / 2f, DELTA);
        assertEquals(expectedCenterY, (rect.getTop() + rect.getBottom()) / 2f, DELTA);
    }
}
