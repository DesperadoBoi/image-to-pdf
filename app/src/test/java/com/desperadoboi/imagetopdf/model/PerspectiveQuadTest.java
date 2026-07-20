package com.desperadoboi.imagetopdf.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class PerspectiveQuadTest {
    @Test
    public void fullUsesImageCornersAndIsClockwise() {
        assertEquals(new NormalizedPoint(0f, 0f), PerspectiveQuad.FULL.getTopLeft());
        assertEquals(new NormalizedPoint(1f, 0f), PerspectiveQuad.FULL.getTopRight());
        assertEquals(new NormalizedPoint(1f, 1f), PerspectiveQuad.FULL.getBottomRight());
        assertEquals(new NormalizedPoint(0f, 1f), PerspectiveQuad.FULL.getBottomLeft());
        assertTrue(PerspectiveQuad.FULL.isFull());
        assertTrue(PerspectiveQuadValidator.signedArea(PerspectiveQuad.FULL) > 0f);
    }

    @Test
    public void acceptsConvexDocumentAndImplementsEquality() {
        PerspectiveQuad first = trapezoid();
        PerspectiveQuad second = trapezoid();

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, PerspectiveQuad.FULL);
    }

    @Test
    public void rejectsCounterClockwiseSelfIntersectionAndTinyArea() {
        assertThrows(IllegalArgumentException.class, () -> new PerspectiveQuad(
                point(0f, 0f), point(0f, 1f), point(1f, 1f), point(1f, 0f)
        ));
        assertThrows(IllegalArgumentException.class, () -> new PerspectiveQuad(
                point(0f, 0f), point(1f, 1f), point(1f, 0f), point(0f, 1f)
        ));
        assertThrows(IllegalArgumentException.class, () -> new PerspectiveQuad(
                point(0f, 0f), point(0.001f, 0f), point(0.001f, 0.001f), point(0f, 0.001f)
        ));
    }

    @Test
    public void pointsOutsideRangeAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> point(1.1f, 0f));
    }

    @Test
    public void fourClockwiseRotationsRestoreQuad() {
        PerspectiveQuad original = trapezoid();
        PerspectiveQuad rotated = original;
        for (int index = 0; index < 4; index++) {
            rotated = rotated.rotateClockwise();
        }

        assertEquals(original, rotated);
    }

    @Test
    public void rotationNinetyOneEightyAndTwoSeventyPreservesCornerOrder() {
        PerspectiveQuad original = trapezoid();

        PerspectiveQuad ninety = original.rotateClockwise();
        PerspectiveQuad oneEighty = ninety.rotateClockwise();
        PerspectiveQuad twoSeventy = oneEighty.rotateClockwise();

        assertEquals(original.getBottomLeft().rotateClockwise(), ninety.getTopLeft());
        assertEquals(original.getTopLeft().rotateClockwise(), ninety.getTopRight());
        assertEquals(original.getTopRight().rotateClockwise(), ninety.getBottomRight());
        assertEquals(original.getBottomRight().rotateClockwise(), ninety.getBottomLeft());
        assertTrue(PerspectiveQuadValidator.signedArea(oneEighty) > 0f);
        assertTrue(PerspectiveQuadValidator.signedArea(twoSeventy) > 0f);
    }

    private PerspectiveQuad trapezoid() {
        return new PerspectiveQuad(
                point(0.2f, 0.1f),
                point(0.8f, 0.15f),
                point(0.95f, 0.9f),
                point(0.05f, 0.85f)
        );
    }

    private NormalizedPoint point(float x, float y) {
        return new NormalizedPoint(x, y);
    }
}
