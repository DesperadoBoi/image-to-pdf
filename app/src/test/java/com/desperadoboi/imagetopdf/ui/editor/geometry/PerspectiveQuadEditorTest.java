package com.desperadoboi.imagetopdf.ui.editor.geometry;

import com.desperadoboi.imagetopdf.model.NormalizedPoint;
import com.desperadoboi.imagetopdf.model.PerspectiveQuad;
import com.desperadoboi.imagetopdf.model.PerspectiveQuadValidator;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class PerspectiveQuadEditorTest {
    @Test
    public void movesEachCornerIndependently() {
        PerspectiveQuad original = insetQuad();

        assertCornerMove(original, PerspectiveQuadEditor.Handle.TOP_LEFT, point(0.05f, 0.1f), 0);
        assertCornerMove(original, PerspectiveQuadEditor.Handle.TOP_RIGHT, point(0.95f, 0.1f), 1);
        assertCornerMove(original, PerspectiveQuadEditor.Handle.BOTTOM_RIGHT, point(0.95f, 0.9f), 2);
        assertCornerMove(original, PerspectiveQuadEditor.Handle.BOTTOM_LEFT, point(0.05f, 0.9f), 3);
    }

    @Test
    public void topMidpointMovesExactlyTopEdge() {
        PerspectiveQuad moved = move(PerspectiveQuad.FULL, PerspectiveQuadEditor.Handle.TOP, 0.5f, 0.2f);

        assertEquals(point(0f, 0.2f), moved.getTopLeft());
        assertEquals(point(1f, 0.2f), moved.getTopRight());
        assertEquals(PerspectiveQuad.FULL.getBottomRight(), moved.getBottomRight());
        assertEquals(PerspectiveQuad.FULL.getBottomLeft(), moved.getBottomLeft());
    }

    @Test
    public void rightMidpointMovesExactlyRightEdge() {
        PerspectiveQuad moved = move(PerspectiveQuad.FULL, PerspectiveQuadEditor.Handle.RIGHT, 0.8f, 0.5f);

        assertEquals(PerspectiveQuad.FULL.getTopLeft(), moved.getTopLeft());
        assertEquals(point(0.8f, 0f), moved.getTopRight());
        assertEquals(point(0.8f, 1f), moved.getBottomRight());
        assertEquals(PerspectiveQuad.FULL.getBottomLeft(), moved.getBottomLeft());
    }

    @Test
    public void bottomMidpointMovesExactlyBottomEdge() {
        PerspectiveQuad moved = move(
                PerspectiveQuad.FULL,
                PerspectiveQuadEditor.Handle.BOTTOM,
                0.5f,
                0.8f
        );

        assertEquals(PerspectiveQuad.FULL.getTopLeft(), moved.getTopLeft());
        assertEquals(PerspectiveQuad.FULL.getTopRight(), moved.getTopRight());
        assertEquals(point(1f, 0.8f), moved.getBottomRight());
        assertEquals(point(0f, 0.8f), moved.getBottomLeft());
    }

    @Test
    public void leftMidpointMovesExactlyLeftEdge() {
        PerspectiveQuad moved = move(PerspectiveQuad.FULL, PerspectiveQuadEditor.Handle.LEFT, 0.2f, 0.5f);

        assertEquals(point(0.2f, 0f), moved.getTopLeft());
        assertEquals(PerspectiveQuad.FULL.getTopRight(), moved.getTopRight());
        assertEquals(PerspectiveQuad.FULL.getBottomRight(), moved.getBottomRight());
        assertEquals(point(0.2f, 1f), moved.getBottomLeft());
    }

    @Test
    public void edgeMovementClampsBothPointsTogether() {
        PerspectiveQuad moved = move(insetQuad(), PerspectiveQuadEditor.Handle.TOP, 0.5f, -1f);

        assertEquals(point(0.2f, 0f), moved.getTopLeft());
        assertEquals(point(0.8f, 0f), moved.getTopRight());
        assertEquals(insetQuad().getBottomRight(), moved.getBottomRight());
        assertEquals(insetQuad().getBottomLeft(), moved.getBottomLeft());
    }

    @Test
    public void invalidSelfIntersectionAndTinyAreaAreRejected() {
        PerspectiveQuad original = PerspectiveQuad.FULL;

        PerspectiveQuad crossing = move(
                original,
                PerspectiveQuadEditor.Handle.TOP_LEFT,
                0.9f,
                0.9f
        );
        PerspectiveQuad collapsed = move(
                original,
                PerspectiveQuadEditor.Handle.TOP,
                0.5f,
                0.999f
        );

        assertSame(original, crossing);
        assertSame(original, collapsed);
    }

    @Test
    public void acceptedEditsRemainClockwiseAndConvex() {
        PerspectiveQuad moved = move(insetQuad(), PerspectiveQuadEditor.Handle.TOP_LEFT, 0.05f, 0.3f);

        assertTrue(PerspectiveQuadValidator.isValid(
                moved.getTopLeft(),
                moved.getTopRight(),
                moved.getBottomRight(),
                moved.getBottomLeft()
        ));
        assertTrue(PerspectiveQuadValidator.signedArea(moved) > 0f);
    }

    private void assertCornerMove(
            PerspectiveQuad original,
            PerspectiveQuadEditor.Handle handle,
            NormalizedPoint target,
            int movedIndex
    ) {
        PerspectiveQuad moved = move(original, handle, target.getX(), target.getY());
        NormalizedPoint[] before = points(original);
        NormalizedPoint[] after = points(moved);
        for (int index = 0; index < before.length; index++) {
            assertEquals(index == movedIndex ? target : before[index], after[index]);
        }
    }

    private PerspectiveQuad move(
            PerspectiveQuad quad,
            PerspectiveQuadEditor.Handle handle,
            float x,
            float y
    ) {
        return PerspectiveQuadEditor.moveHandle(quad, handle, x, y);
    }

    private PerspectiveQuad insetQuad() {
        return new PerspectiveQuad(
                point(0.2f, 0.2f),
                point(0.8f, 0.2f),
                point(0.8f, 0.8f),
                point(0.2f, 0.8f)
        );
    }

    private NormalizedPoint[] points(PerspectiveQuad quad) {
        return new NormalizedPoint[]{
                quad.getTopLeft(),
                quad.getTopRight(),
                quad.getBottomRight(),
                quad.getBottomLeft()
        };
    }

    private NormalizedPoint point(float x, float y) {
        return new NormalizedPoint(x, y);
    }
}
