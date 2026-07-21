package com.desperadoboi.imagetopdf.model;

import java.util.Objects;

public final class NormalizedPoint {
    private static final float COORDINATE_PRECISION = 1_000_000f;

    private final float x;
    private final float y;

    public NormalizedPoint(float x, float y) {
        validateCoordinate(x, "x");
        validateCoordinate(y, "y");
        this.x = canonicalize(x);
        this.y = canonicalize(y);
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public NormalizedPoint rotateClockwise() {
        return new NormalizedPoint(1f - y, x);
    }

    public NormalizedPoint rotateCounterClockwise() {
        return new NormalizedPoint(y, 1f - x);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof NormalizedPoint)) {
            return false;
        }
        NormalizedPoint point = (NormalizedPoint) other;
        return Float.compare(x, point.x) == 0 && Float.compare(y, point.y) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    String toKey() {
        return Integer.toHexString(Float.floatToIntBits(x))
                + ":"
                + Integer.toHexString(Float.floatToIntBits(y));
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
