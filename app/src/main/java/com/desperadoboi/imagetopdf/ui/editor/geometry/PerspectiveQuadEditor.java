package com.desperadoboi.imagetopdf.ui.editor.geometry;

import com.desperadoboi.imagetopdf.model.NormalizedPoint;
import com.desperadoboi.imagetopdf.model.PerspectiveQuad;

import java.util.Objects;

public final class PerspectiveQuadEditor {
    private PerspectiveQuadEditor() {
    }

    public static PerspectiveQuad moveHandle(
            PerspectiveQuad quad,
            Handle handle,
            float targetX,
            float targetY
    ) {
        Objects.requireNonNull(quad, "quad is required");
        Objects.requireNonNull(handle, "handle is required");
        if (!Float.isFinite(targetX) || !Float.isFinite(targetY)) {
            return quad;
        }
        NormalizedPoint target = new NormalizedPoint(clamp(targetX), clamp(targetY));
        switch (handle) {
            case TOP_LEFT:
                return createOrKeep(
                        quad,
                        target,
                        quad.getTopRight(),
                        quad.getBottomRight(),
                        quad.getBottomLeft()
                );
            case TOP_RIGHT:
                return createOrKeep(
                        quad,
                        quad.getTopLeft(),
                        target,
                        quad.getBottomRight(),
                        quad.getBottomLeft()
                );
            case BOTTOM_RIGHT:
                return createOrKeep(
                        quad,
                        quad.getTopLeft(),
                        quad.getTopRight(),
                        target,
                        quad.getBottomLeft()
                );
            case BOTTOM_LEFT:
                return createOrKeep(
                        quad,
                        quad.getTopLeft(),
                        quad.getTopRight(),
                        quad.getBottomRight(),
                        target
                );
            case TOP:
                return moveEdge(quad, target, quad.getTopLeft(), quad.getTopRight(), handle);
            case RIGHT:
                return moveEdge(quad, target, quad.getTopRight(), quad.getBottomRight(), handle);
            case BOTTOM:
                return moveEdge(quad, target, quad.getBottomLeft(), quad.getBottomRight(), handle);
            case LEFT:
                return moveEdge(quad, target, quad.getTopLeft(), quad.getBottomLeft(), handle);
            default:
                return quad;
        }
    }

    public static NormalizedPoint midpoint(NormalizedPoint first, NormalizedPoint second) {
        return new NormalizedPoint(
                (first.getX() + second.getX()) / 2f,
                (first.getY() + second.getY()) / 2f
        );
    }

    private static PerspectiveQuad moveEdge(
            PerspectiveQuad quad,
            NormalizedPoint target,
            NormalizedPoint first,
            NormalizedPoint second,
            Handle handle
    ) {
        NormalizedPoint currentMidpoint = midpoint(first, second);
        float deltaX = clampDelta(
                target.getX() - currentMidpoint.getX(),
                first.getX(),
                second.getX()
        );
        float deltaY = clampDelta(
                target.getY() - currentMidpoint.getY(),
                first.getY(),
                second.getY()
        );
        NormalizedPoint movedFirst = offset(first, deltaX, deltaY);
        NormalizedPoint movedSecond = offset(second, deltaX, deltaY);
        switch (handle) {
            case TOP:
                return createOrKeep(
                        quad,
                        movedFirst,
                        movedSecond,
                        quad.getBottomRight(),
                        quad.getBottomLeft()
                );
            case RIGHT:
                return createOrKeep(
                        quad,
                        quad.getTopLeft(),
                        movedFirst,
                        movedSecond,
                        quad.getBottomLeft()
                );
            case BOTTOM:
                return createOrKeep(
                        quad,
                        quad.getTopLeft(),
                        quad.getTopRight(),
                        movedSecond,
                        movedFirst
                );
            case LEFT:
                return createOrKeep(
                        quad,
                        movedFirst,
                        quad.getTopRight(),
                        quad.getBottomRight(),
                        movedSecond
                );
            default:
                return quad;
        }
    }

    private static PerspectiveQuad createOrKeep(
            PerspectiveQuad original,
            NormalizedPoint topLeft,
            NormalizedPoint topRight,
            NormalizedPoint bottomRight,
            NormalizedPoint bottomLeft
    ) {
        try {
            return new PerspectiveQuad(topLeft, topRight, bottomRight, bottomLeft);
        } catch (IllegalArgumentException exception) {
            return original;
        }
    }

    private static NormalizedPoint offset(NormalizedPoint point, float deltaX, float deltaY) {
        return new NormalizedPoint(
                clamp(point.getX() + deltaX),
                clamp(point.getY() + deltaY)
        );
    }

    private static float clampDelta(float delta, float first, float second) {
        float minimum = Math.max(-first, -second);
        float maximum = Math.min(1f - first, 1f - second);
        return Math.max(minimum, Math.min(maximum, delta));
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    public enum Handle {
        TOP_LEFT,
        TOP,
        TOP_RIGHT,
        RIGHT,
        BOTTOM_RIGHT,
        BOTTOM,
        BOTTOM_LEFT,
        LEFT
    }
}
