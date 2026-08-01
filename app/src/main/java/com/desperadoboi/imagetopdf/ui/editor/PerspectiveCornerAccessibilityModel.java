package com.desperadoboi.imagetopdf.ui.editor;

import com.desperadoboi.imagetopdf.model.NormalizedPoint;
import com.desperadoboi.imagetopdf.model.PerspectiveQuad;
import com.desperadoboi.imagetopdf.ui.editor.geometry.PerspectiveQuadEditor;

public final class PerspectiveCornerAccessibilityModel {
    public static final int INVALID_VIRTUAL_ID = -1;
    public static final int TOP_LEFT_ID = 1;
    public static final int TOP_RIGHT_ID = 2;
    public static final int BOTTOM_LEFT_ID = 3;
    public static final int BOTTOM_RIGHT_ID = 4;
    public static final int VIRTUAL_CHILD_COUNT = 4;
    public static final float DEFAULT_STEP = 0.015f;

    private PerspectiveCornerAccessibilityModel() {
    }

    public static boolean isCornerId(int virtualId) {
        return virtualId >= TOP_LEFT_ID && virtualId <= BOTTOM_RIGHT_ID;
    }

    public static PerspectiveQuad move(
            PerspectiveQuad quad,
            int virtualId,
            Direction direction,
            float step
    ) {
        if (!isCornerId(virtualId) || direction == null || !Float.isFinite(step) || step <= 0f) {
            return quad;
        }
        NormalizedPoint point = pointFor(quad, virtualId);
        float targetX = point.getX();
        float targetY = point.getY();
        switch (direction) {
            case LEFT:
                targetX -= step;
                break;
            case RIGHT:
                targetX += step;
                break;
            case UP:
                targetY -= step;
                break;
            case DOWN:
                targetY += step;
                break;
            default:
                return quad;
        }
        PerspectiveQuad moved = PerspectiveQuadEditor.moveHandle(
                quad,
                handleFor(virtualId),
                targetX,
                targetY
        );
        return moved.equals(quad) ? quad : moved;
    }

    public static int hitTest(
            PerspectiveQuad quad,
            float imageLeft,
            float imageTop,
            float imageRight,
            float imageBottom,
            int hostWidth,
            int hostHeight,
            float targetSize,
            float x,
            float y
    ) {
        int closestId = INVALID_VIRTUAL_ID;
        float closestDistance = Float.MAX_VALUE;
        for (int virtualId = TOP_LEFT_ID; virtualId <= BOTTOM_RIGHT_ID; virtualId++) {
            Bounds bounds = boundsFor(
                    quad,
                    virtualId,
                    imageLeft,
                    imageTop,
                    imageRight,
                    imageBottom,
                    hostWidth,
                    hostHeight,
                    targetSize
            );
            if (!bounds.contains(x, y)) continue;
            NormalizedPoint point = pointFor(quad, virtualId);
            float centerX = imageLeft + (point.getX() * (imageRight - imageLeft));
            float centerY = imageTop + (point.getY() * (imageBottom - imageTop));
            float deltaX = x - centerX;
            float deltaY = y - centerY;
            float distance = (deltaX * deltaX) + (deltaY * deltaY);
            if (distance < closestDistance) {
                closestId = virtualId;
                closestDistance = distance;
            }
        }
        return closestId;
    }

    public static Bounds boundsFor(
            PerspectiveQuad quad,
            int virtualId,
            float imageLeft,
            float imageTop,
            float imageRight,
            float imageBottom,
            int hostWidth,
            int hostHeight,
            float targetSize
    ) {
        if (!isCornerId(virtualId) || hostWidth <= 0 || hostHeight <= 0) {
            return Bounds.EMPTY;
        }
        NormalizedPoint point = pointFor(quad, virtualId);
        float centerX = imageLeft + (point.getX() * (imageRight - imageLeft));
        float centerY = imageTop + (point.getY() * (imageBottom - imageTop));
        int width = Math.min(hostWidth, Math.max(1, Math.round(targetSize)));
        int height = Math.min(hostHeight, Math.max(1, Math.round(targetSize)));
        int left = clamp(Math.round(centerX) - (width / 2), 0, hostWidth - width);
        int top = clamp(Math.round(centerY) - (height / 2), 0, hostHeight - height);
        return new Bounds(left, top, left + width, top + height);
    }

    public static NormalizedPoint pointFor(PerspectiveQuad quad, int virtualId) {
        switch (virtualId) {
            case TOP_LEFT_ID:
                return quad.getTopLeft();
            case TOP_RIGHT_ID:
                return quad.getTopRight();
            case BOTTOM_LEFT_ID:
                return quad.getBottomLeft();
            case BOTTOM_RIGHT_ID:
                return quad.getBottomRight();
            default:
                throw new IllegalArgumentException("Unknown corner virtual id");
        }
    }

    public static int positionPercent(float coordinate) {
        return Math.round(coordinate * 100f);
    }

    private static PerspectiveQuadEditor.Handle handleFor(int virtualId) {
        switch (virtualId) {
            case TOP_LEFT_ID:
                return PerspectiveQuadEditor.Handle.TOP_LEFT;
            case TOP_RIGHT_ID:
                return PerspectiveQuadEditor.Handle.TOP_RIGHT;
            case BOTTOM_LEFT_ID:
                return PerspectiveQuadEditor.Handle.BOTTOM_LEFT;
            case BOTTOM_RIGHT_ID:
                return PerspectiveQuadEditor.Handle.BOTTOM_RIGHT;
            default:
                throw new IllegalArgumentException("Unknown corner virtual id");
        }
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public enum Direction {
        LEFT,
        RIGHT,
        UP,
        DOWN
    }

    public static final class Bounds {
        private static final Bounds EMPTY = new Bounds(0, 0, 0, 0);

        private final int left;
        private final int top;
        private final int right;
        private final int bottom;

        private Bounds(int left, int top, int right, int bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        public int getLeft() {
            return left;
        }

        public int getTop() {
            return top;
        }

        public int getRight() {
            return right;
        }

        public int getBottom() {
            return bottom;
        }

        public boolean contains(float x, float y) {
            return x >= left && x < right && y >= top && y < bottom;
        }
    }
}
