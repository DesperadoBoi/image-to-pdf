package com.desperadoboi.imagetopdf.ui.editor;

import com.desperadoboi.imagetopdf.model.NormalizedPoint;
import com.desperadoboi.imagetopdf.model.PerspectiveQuad;
import com.desperadoboi.imagetopdf.model.PerspectiveQuadValidator;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class PerspectiveCornerAccessibilityModelTest {
    @Test public void exposesExactlyFourCornerChildren() {
        assertEquals(4, PerspectiveCornerAccessibilityModel.VIRTUAL_CHILD_COUNT);
        assertTrue(PerspectiveCornerAccessibilityModel.isCornerId(
                PerspectiveCornerAccessibilityModel.TOP_LEFT_ID
        ));
        assertTrue(PerspectiveCornerAccessibilityModel.isCornerId(
                PerspectiveCornerAccessibilityModel.TOP_RIGHT_ID
        ));
        assertTrue(PerspectiveCornerAccessibilityModel.isCornerId(
                PerspectiveCornerAccessibilityModel.BOTTOM_LEFT_ID
        ));
        assertTrue(PerspectiveCornerAccessibilityModel.isCornerId(
                PerspectiveCornerAccessibilityModel.BOTTOM_RIGHT_ID
        ));
    }

    @Test public void virtualBoundsRemainInsideHostAtImageEdges() {
        for (int id = PerspectiveCornerAccessibilityModel.TOP_LEFT_ID;
             id <= PerspectiveCornerAccessibilityModel.BOTTOM_RIGHT_ID;
             id++) {
            PerspectiveCornerAccessibilityModel.Bounds bounds =
                    PerspectiveCornerAccessibilityModel.boundsFor(
                            PerspectiveQuad.FULL,
                            id,
                            0f,
                            0f,
                            320f,
                            200f,
                            320,
                            200,
                            48f
                    );
            assertTrue(bounds.getLeft() >= 0);
            assertTrue(bounds.getTop() >= 0);
            assertTrue(bounds.getRight() <= 320);
            assertTrue(bounds.getBottom() <= 200);
            assertEquals(48, bounds.getRight() - bounds.getLeft());
            assertEquals(48, bounds.getBottom() - bounds.getTop());
        }
    }

    @Test public void touchExplorationHitTestFindsEachCornerOnly() {
        assertEquals(PerspectiveCornerAccessibilityModel.TOP_LEFT_ID, hit(12f, 12f));
        assertEquals(PerspectiveCornerAccessibilityModel.TOP_RIGHT_ID, hit(308f, 12f));
        assertEquals(PerspectiveCornerAccessibilityModel.BOTTOM_LEFT_ID, hit(12f, 188f));
        assertEquals(PerspectiveCornerAccessibilityModel.BOTTOM_RIGHT_ID, hit(308f, 188f));
        assertEquals(PerspectiveCornerAccessibilityModel.INVALID_VIRTUAL_ID, hit(160f, 100f));
    }

    @Test public void everyDirectionMovesOnlyTheFocusedCorner() {
        PerspectiveQuad original = insetQuad();

        assertEquals(0.185f, move(original, Direction.LEFT).getTopLeft().getX(), 0.0001f);
        assertEquals(0.215f, move(original, Direction.RIGHT).getTopLeft().getX(), 0.0001f);
        assertEquals(0.185f, move(original, Direction.UP).getTopLeft().getY(), 0.0001f);
        assertEquals(0.215f, move(original, Direction.DOWN).getTopLeft().getY(), 0.0001f);
        assertEquals(original.getTopRight(), move(original, Direction.RIGHT).getTopRight());
        assertEquals(original.getBottomLeft(), move(original, Direction.DOWN).getBottomLeft());
        assertEquals(original.getBottomRight(), move(original, Direction.LEFT).getBottomRight());
    }

    @Test public void boundaryMovementIsClamped() {
        PerspectiveQuad moved = PerspectiveCornerAccessibilityModel.move(
                PerspectiveQuad.FULL,
                PerspectiveCornerAccessibilityModel.TOP_LEFT_ID,
                PerspectiveCornerAccessibilityModel.Direction.LEFT,
                PerspectiveCornerAccessibilityModel.DEFAULT_STEP
        );

        assertSame(PerspectiveQuad.FULL, moved);
    }

    @Test public void movementCannotCreateSelfIntersection() {
        PerspectiveQuad original = insetQuad();
        PerspectiveQuad moved = PerspectiveCornerAccessibilityModel.move(
                original,
                PerspectiveCornerAccessibilityModel.TOP_LEFT_ID,
                PerspectiveCornerAccessibilityModel.Direction.RIGHT,
                0.7f
        );

        assertSame(original, moved);
        assertTrue(PerspectiveQuadValidator.isValid(
                moved.getTopLeft(),
                moved.getTopRight(),
                moved.getBottomRight(),
                moved.getBottomLeft()
        ));
    }

    @Test public void dpadModelChangesRequestedBottomRightCorner() {
        PerspectiveQuad original = insetQuad();
        PerspectiveQuad moved = PerspectiveCornerAccessibilityModel.move(
                original,
                PerspectiveCornerAccessibilityModel.BOTTOM_RIGHT_ID,
                PerspectiveCornerAccessibilityModel.Direction.UP,
                PerspectiveCornerAccessibilityModel.DEFAULT_STEP
        );

        assertNotEquals(original, moved);
        assertEquals(0.785f, moved.getBottomRight().getY(), 0.0001f);
        assertEquals(original.getTopLeft(), moved.getTopLeft());
        assertEquals(original.getTopRight(), moved.getTopRight());
        assertEquals(original.getBottomLeft(), moved.getBottomLeft());
    }

    private int hit(float x, float y) {
        return PerspectiveCornerAccessibilityModel.hitTest(
                PerspectiveQuad.FULL,
                0f,
                0f,
                320f,
                200f,
                320,
                200,
                48f,
                x,
                y
        );
    }

    private PerspectiveQuad move(PerspectiveQuad quad, Direction direction) {
        return PerspectiveCornerAccessibilityModel.move(
                quad,
                PerspectiveCornerAccessibilityModel.TOP_LEFT_ID,
                PerspectiveCornerAccessibilityModel.Direction.valueOf(direction.name()),
                PerspectiveCornerAccessibilityModel.DEFAULT_STEP
        );
    }

    private PerspectiveQuad insetQuad() {
        return new PerspectiveQuad(
                new NormalizedPoint(0.2f, 0.2f),
                new NormalizedPoint(0.8f, 0.2f),
                new NormalizedPoint(0.8f, 0.8f),
                new NormalizedPoint(0.2f, 0.8f)
        );
    }

    private enum Direction {
        LEFT,
        RIGHT,
        UP,
        DOWN
    }
}
