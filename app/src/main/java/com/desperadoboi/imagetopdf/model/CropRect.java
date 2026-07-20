package com.desperadoboi.imagetopdf.model;

import java.util.Objects;

public final class CropRect {
    public static final CropRect FULL = new CropRect(0f, 0f, 1f, 1f);
    private static final float COORDINATE_PRECISION = 1_000_000f;

    private final float left;
    private final float top;
    private final float right;
    private final float bottom;

    public CropRect(float left, float top, float right, float bottom) {
        validateCoordinate(left, "left");
        validateCoordinate(top, "top");
        validateCoordinate(right, "right");
        validateCoordinate(bottom, "bottom");
        float normalizedLeft = canonicalize(left);
        float normalizedTop = canonicalize(top);
        float normalizedRight = canonicalize(right);
        float normalizedBottom = canonicalize(bottom);
        if (normalizedRight <= normalizedLeft || normalizedBottom <= normalizedTop) {
            throw new IllegalArgumentException("Crop rectangle must have positive area");
        }
        this.left = normalizedLeft;
        this.top = normalizedTop;
        this.right = normalizedRight;
        this.bottom = normalizedBottom;
    }

    public float getLeft() {
        return left;
    }

    public float getTop() {
        return top;
    }

    public float getRight() {
        return right;
    }

    public float getBottom() {
        return bottom;
    }

    public float getWidth() {
        return right - left;
    }

    public float getHeight() {
        return bottom - top;
    }

    public boolean isFull() {
        return equals(FULL);
    }

    public CropRect rotateClockwise() {
        return new CropRect(1f - bottom, left, 1f - top, right);
    }

    public CropRect rotateCounterClockwise() {
        return new CropRect(top, 1f - right, bottom, 1f - left);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CropRect)) {
            return false;
        }
        CropRect rect = (CropRect) other;
        return Float.compare(left, rect.left) == 0
                && Float.compare(top, rect.top) == 0
                && Float.compare(right, rect.right) == 0
                && Float.compare(bottom, rect.bottom) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(left, top, right, bottom);
    }

    String toKey() {
        return Integer.toHexString(Float.floatToIntBits(left))
                + ":" + Integer.toHexString(Float.floatToIntBits(top))
                + ":" + Integer.toHexString(Float.floatToIntBits(right))
                + ":" + Integer.toHexString(Float.floatToIntBits(bottom));
    }

    private static void validateCoordinate(float value, String name) {
        if (!Float.isFinite(value) || value < 0f || value > 1f) {
            throw new IllegalArgumentException(name + " must be finite and in range 0..1");
        }
    }

    private static float canonicalize(float value) {
        return Math.round(value * COORDINATE_PRECISION) / COORDINATE_PRECISION;
    }
}
