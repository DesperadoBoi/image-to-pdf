package com.desperadoboi.imagetopdf.pdf;

import com.desperadoboi.imagetopdf.model.ImagePlacementMode;
import com.desperadoboi.imagetopdf.util.ImagePlacementCalculator;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class RasterTargetCalculatorTest {
    private static final double ASPECT_DELTA = 0.01d;

    @Test
    public void fitA4PortraitDestinationUsesBalancedDpi() {
        RasterTarget target = calculateFit(4000, 6000, 547f, 794f);

        assertEquals(1059, target.getTargetWidthPixels());
        assertEquals(1588, target.getTargetHeightPixels());
        assertAspectRatioPreserved(target, 4000d / 6000d);
    }

    @Test
    public void fitHorizontalAreaKeepsAspectRatio() {
        RasterTarget target = calculateFit(4000, 2000, 500f, 250f);

        assertEquals(1000, target.getTargetWidthPixels());
        assertEquals(500, target.getTargetHeightPixels());
        assertAspectRatioPreserved(target, 2d);
    }

    @Test
    public void fitVerticalAreaKeepsAspectRatio() {
        RasterTarget target = calculateFit(2000, 4000, 250f, 500f);

        assertEquals(500, target.getTargetWidthPixels());
        assertEquals(1000, target.getTargetHeightPixels());
        assertAspectRatioPreserved(target, 0.5d);
    }

    @Test
    public void fitSmallSourceIsNotUpscaled() {
        RasterTarget target = calculateFit(400, 600, 547f, 794f);

        assertEquals(400, target.getTargetWidthPixels());
        assertEquals(600, target.getTargetHeightPixels());
    }

    @Test
    public void fitLargeSourceIsReduced() {
        RasterTarget target = calculateFit(8000, 12000, 547f, 794f);

        assertTrue(target.getTargetWidthPixels() < 8000);
        assertTrue(target.getTargetHeightPixels() < 12000);
        assertAspectRatioPreserved(target, 8000d / 12000d);
    }

    @Test
    public void fillWideSourceForVerticalDestinationKeepsEnoughCropResolution() {
        RasterTarget target = calculateFill(8000, 4000, 500f, 1000f);

        assertEquals(4000, target.getTargetWidthPixels());
        assertEquals(2000, target.getTargetHeightPixels());
        assertFillCropCoversDestinationTarget(target, 500f, 1000f);
        assertDoesNotExceedSource(target, 8000, 4000);
        assertAspectRatioPreserved(target, 2d);
    }

    @Test
    public void fillTallSourceForHorizontalDestinationKeepsEnoughCropResolution() {
        RasterTarget target = calculateFill(4000, 8000, 1000f, 500f);

        assertEquals(2000, target.getTargetWidthPixels());
        assertEquals(4000, target.getTargetHeightPixels());
        assertFillCropCoversDestinationTarget(target, 1000f, 500f);
        assertDoesNotExceedSource(target, 4000, 8000);
        assertAspectRatioPreserved(target, 0.5d);
    }

    @Test
    public void fillSquareSourceForRectangularDestinationKeepsEnoughCropResolution() {
        RasterTarget target = calculateFill(4000, 4000, 500f, 250f);

        assertEquals(1000, target.getTargetWidthPixels());
        assertEquals(1000, target.getTargetHeightPixels());
        assertFillCropCoversDestinationTarget(target, 500f, 250f);
        assertAspectRatioPreserved(target, 1d);
    }

    @Test
    public void fillSmallSourceIsNotUpscaled() {
        RasterTarget target = calculateFill(800, 400, 500f, 1000f);

        assertEquals(800, target.getTargetWidthPixels());
        assertEquals(400, target.getTargetHeightPixels());
    }

    @Test
    public void seventyTwoPointsAtBalancedDpiGivesOneHundredFortyFourPixels() {
        RasterTarget target = calculateFit(1000, 1000, 72f, 72f);

        assertEquals(144, target.getTargetWidthPixels());
        assertEquals(144, target.getTargetHeightPixels());
    }

    @Test
    public void fractionalPointDimensionsAreRoundedUp() {
        RasterTarget target = calculateFit(1000, 1000, 10.1f, 10.1f);

        assertEquals(21, target.getTargetWidthPixels());
        assertEquals(21, target.getTargetHeightPixels());
    }

    @Test
    public void zeroAndNegativeDimensionsAreRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> calculateFit(0, 100, 72f, 72f)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> calculateFit(100, 100, 0f, 72f)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> calculateFit(100, 100, -1f, 72f)
        );
    }

    @Test
    public void manualRotationNinetySwapsCalculationDimensions() {
        RasterTarget target = calculateWithRotation(4000, 8000, 90);

        assertEquals(1000, target.getTargetWidthPixels());
        assertEquals(500, target.getTargetHeightPixels());
    }

    @Test
    public void manualRotationTwoHundredSeventySwapsCalculationDimensions() {
        RasterTarget target = calculateWithRotation(4000, 8000, 270);

        assertEquals(1000, target.getTargetWidthPixels());
        assertEquals(500, target.getTargetHeightPixels());
    }

    @Test
    public void manualRotationZeroAndOneHundredEightyKeepCalculationDimensions() {
        RasterTarget zero = calculateWithRotation(4000, 8000, 0);
        RasterTarget oneEighty = calculateWithRotation(4000, 8000, 180);

        assertEquals(1000, zero.getTargetWidthPixels());
        assertEquals(2000, zero.getTargetHeightPixels());
        assertEquals(zero.getTargetWidthPixels(), oneEighty.getTargetWidthPixels());
        assertEquals(zero.getTargetHeightPixels(), oneEighty.getTargetHeightPixels());
    }

    @Test
    public void veryLargeDimensionsDoNotOverflow() {
        RasterTarget target = calculateFit(
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                1_000_000_000f,
                1_000_000_000f
        );

        assertTrue(target.getTargetWidthPixels() > 0);
        assertTrue(target.getTargetHeightPixels() > 0);
        assertDoesNotExceedSource(target, Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    @Test
    public void targetIsAlwaysPositiveWhenSourceIsTiny() {
        RasterTarget target = calculateFit(1, 1, 72f, 72f);

        assertEquals(1, target.getTargetWidthPixels());
        assertEquals(1, target.getTargetHeightPixels());
    }

    @Test
    public void nullPlacementModeIsRejected() {
        assertThrows(
                NullPointerException.class,
                () -> RasterTargetCalculator.calculate(100, 100, 0f, 0f, 72f, 72f, null)
        );
    }

    private RasterTarget calculateFit(int sourceWidth, int sourceHeight, float contentWidth, float contentHeight) {
        return RasterTargetCalculator.calculate(
                sourceWidth,
                sourceHeight,
                0f,
                0f,
                contentWidth,
                contentHeight,
                ImagePlacementMode.FIT
        );
    }

    private RasterTarget calculateFill(int sourceWidth, int sourceHeight, float contentWidth, float contentHeight) {
        return RasterTargetCalculator.calculate(
                sourceWidth,
                sourceHeight,
                0f,
                0f,
                contentWidth,
                contentHeight,
                ImagePlacementMode.FILL
        );
    }

    private RasterTarget calculateWithRotation(int sourceWidth, int sourceHeight, int rotationDegrees) {
        return RasterTargetCalculator.calculate(
                sourceWidth,
                sourceHeight,
                false,
                rotationDegrees,
                0f,
                0f,
                500f,
                1000f,
                ImagePlacementMode.FIT
        );
    }

    private void assertFillCropCoversDestinationTarget(
            RasterTarget target,
            float contentWidth,
            float contentHeight
    ) {
        ImagePlacementCalculator.ImageDrawPlan drawPlan =
                ImagePlacementCalculator.calculateDrawPlan(
                        target.getTargetWidthPixels(),
                        target.getTargetHeightPixels(),
                        0f,
                        0f,
                        contentWidth,
                        contentHeight,
                        ImagePlacementMode.FILL
                );
        ImagePlacementCalculator.PlacementRect sourceRect = drawPlan.getSourceRect();

        assertTrue(sourceRect.getWidth() >= pointsToPixels(contentWidth));
        assertTrue(sourceRect.getHeight() >= pointsToPixels(contentHeight));
    }

    private void assertDoesNotExceedSource(RasterTarget target, int sourceWidth, int sourceHeight) {
        assertTrue(target.getTargetWidthPixels() <= sourceWidth);
        assertTrue(target.getTargetHeightPixels() <= sourceHeight);
    }

    private void assertAspectRatioPreserved(RasterTarget target, double expectedAspectRatio) {
        assertEquals(
                expectedAspectRatio,
                target.getTargetWidthPixels() / (double) target.getTargetHeightPixels(),
                ASPECT_DELTA
        );
    }

    private long pointsToPixels(float points) {
        return (long) Math.ceil(
                points * RasterTargetCalculator.TARGET_DPI / 72d
        );
    }
}
