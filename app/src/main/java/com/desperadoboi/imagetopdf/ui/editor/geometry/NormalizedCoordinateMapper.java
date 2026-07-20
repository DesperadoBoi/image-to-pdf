package com.desperadoboi.imagetopdf.ui.editor.geometry;

import com.desperadoboi.imagetopdf.model.NormalizedPoint;

public final class NormalizedCoordinateMapper {
    private final float left;
    private final float top;
    private final float width;
    private final float height;

    public NormalizedCoordinateMapper(float left, float top, float right, float bottom) {
        if (!Float.isFinite(left)
                || !Float.isFinite(top)
                || !Float.isFinite(right)
                || !Float.isFinite(bottom)
                || right <= left
                || bottom <= top) {
            throw new IllegalArgumentException("Mapping bounds must be finite and positive");
        }
        this.left = left;
        this.top = top;
        this.width = right - left;
        this.height = bottom - top;
    }

    public NormalizedPoint toNormalized(float viewX, float viewY) {
        if (!Float.isFinite(viewX) || !Float.isFinite(viewY)) {
            throw new IllegalArgumentException("View point must be finite");
        }
        return new NormalizedPoint(
                clamp((viewX - left) / width),
                clamp((viewY - top) / height)
        );
    }

    public ViewPoint toView(NormalizedPoint point) {
        if (point == null) {
            throw new NullPointerException("point is required");
        }
        return new ViewPoint(
                left + (point.getX() * width),
                top + (point.getY() * height)
        );
    }

    private float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    public static final class ViewPoint {
        private final float x;
        private final float y;

        public ViewPoint(float x, float y) {
            if (!Float.isFinite(x) || !Float.isFinite(y)) {
                throw new IllegalArgumentException("View point must be finite");
            }
            this.x = x;
            this.y = y;
        }

        public float getX() {
            return x;
        }

        public float getY() {
            return y;
        }
    }
}
