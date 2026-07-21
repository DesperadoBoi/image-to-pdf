package com.desperadoboi.imagetopdf.image;

import com.desperadoboi.imagetopdf.model.NormalizedPoint;
import com.desperadoboi.imagetopdf.model.PerspectiveQuad;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class PerspectiveTargetCalculatorTest {
    @Test
    public void rectangularQuadKeepsItsPixelDimensions() {
        PerspectiveTargetCalculator.Target target = PerspectiveTargetCalculator.calculate(
                1200,
                800,
                PerspectiveQuad.FULL
        );

        assertEquals(1200, target.getWidth());
        assertEquals(800, target.getHeight());
    }

    @Test
    public void trapezoidUsesMaximumOpposingEdgeLengths() {
        PerspectiveQuad trapezoid = quad(
                0.2f, 0.1f,
                0.8f, 0.1f,
                0.95f, 0.9f,
                0.05f, 0.9f
        );

        PerspectiveTargetCalculator.Target target = PerspectiveTargetCalculator.calculate(
                1000,
                1000,
                trapezoid
        );

        assertEquals(900, target.getWidth());
        assertTrue(target.getHeight() >= 800);
    }

    @Test
    public void strongPerspectiveHorizontalAndVerticalTargetsStayPositive() {
        PerspectiveQuad strong = quad(
                0.35f, 0.05f,
                0.65f, 0.08f,
                0.95f, 0.95f,
                0.05f, 0.9f
        );

        PerspectiveTargetCalculator.Target horizontal =
                PerspectiveTargetCalculator.calculate(2000, 800, strong);
        PerspectiveTargetCalculator.Target vertical =
                PerspectiveTargetCalculator.calculate(800, 2000, strong);

        assertTrue(horizontal.getWidth() > horizontal.getHeight());
        assertTrue(vertical.getHeight() > vertical.getWidth());
        assertTrue(horizontal.getWidth() > 0 && horizontal.getHeight() > 0);
        assertTrue(vertical.getWidth() > 0 && vertical.getHeight() > 0);
    }

    @Test
    public void hugeSourceCannotOverflowTarget() {
        PerspectiveTargetCalculator.Target target = PerspectiveTargetCalculator.calculate(
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                PerspectiveQuad.FULL
        );

        assertTrue(target.getWidth() > 0);
        assertTrue(target.getHeight() > 0);
        assertTrue(target.getWidth() <= 8192);
        assertTrue((long) target.getWidth() * target.getHeight() <= 16_000_000L);
    }

    @Test
    public void verySmallQuadIsRejectedBeforeTargetCalculation() {
        assertThrows(IllegalArgumentException.class, () -> quad(
                0f, 0f,
                0.01f, 0f,
                0.01f, 0.01f,
                0f, 0.01f
        ));
    }

    @Test
    public void destinationCornersDescribeCalculatedRectangle() {
        PerspectiveTargetCalculator.Target target = PerspectiveTargetCalculator.calculate(
                1200,
                800,
                PerspectiveQuad.FULL
        );

        assertArrayEquals(
                new float[]{0f, 0f, 1200f, 0f, 1200f, 800f, 0f, 800f},
                target.getDestinationPoints(),
                0f
        );
    }

    private PerspectiveQuad quad(
            float topLeftX,
            float topLeftY,
            float topRightX,
            float topRightY,
            float bottomRightX,
            float bottomRightY,
            float bottomLeftX,
            float bottomLeftY
    ) {
        return new PerspectiveQuad(
                new NormalizedPoint(topLeftX, topLeftY),
                new NormalizedPoint(topRightX, topRightY),
                new NormalizedPoint(bottomRightX, bottomRightY),
                new NormalizedPoint(bottomLeftX, bottomLeftY)
        );
    }
}
