package com.desperadoboi.imagetopdf.model;

import java.util.Objects;

public final class PerspectiveQuad {
    public static final PerspectiveQuad FULL = new PerspectiveQuad(
            new NormalizedPoint(0f, 0f),
            new NormalizedPoint(1f, 0f),
            new NormalizedPoint(1f, 1f),
            new NormalizedPoint(0f, 1f)
    );

    private final NormalizedPoint topLeft;
    private final NormalizedPoint topRight;
    private final NormalizedPoint bottomRight;
    private final NormalizedPoint bottomLeft;

    public PerspectiveQuad(
            NormalizedPoint topLeft,
            NormalizedPoint topRight,
            NormalizedPoint bottomRight,
            NormalizedPoint bottomLeft
    ) {
        this.topLeft = Objects.requireNonNull(topLeft, "topLeft is required");
        this.topRight = Objects.requireNonNull(topRight, "topRight is required");
        this.bottomRight = Objects.requireNonNull(bottomRight, "bottomRight is required");
        this.bottomLeft = Objects.requireNonNull(bottomLeft, "bottomLeft is required");
        if (!PerspectiveQuadValidator.isValid(
                topLeft,
                topRight,
                bottomRight,
                bottomLeft
        )) {
            throw new IllegalArgumentException("Perspective quad must be clockwise and convex");
        }
    }

    public NormalizedPoint getTopLeft() {
        return topLeft;
    }

    public NormalizedPoint getTopRight() {
        return topRight;
    }

    public NormalizedPoint getBottomRight() {
        return bottomRight;
    }

    public NormalizedPoint getBottomLeft() {
        return bottomLeft;
    }

    public boolean isFull() {
        return equals(FULL);
    }

    public PerspectiveQuad rotateClockwise() {
        return new PerspectiveQuad(
                bottomLeft.rotateClockwise(),
                topLeft.rotateClockwise(),
                topRight.rotateClockwise(),
                bottomRight.rotateClockwise()
        );
    }

    public PerspectiveQuad rotateCounterClockwise() {
        return new PerspectiveQuad(
                topRight.rotateCounterClockwise(),
                bottomRight.rotateCounterClockwise(),
                bottomLeft.rotateCounterClockwise(),
                topLeft.rotateCounterClockwise()
        );
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PerspectiveQuad)) {
            return false;
        }
        PerspectiveQuad quad = (PerspectiveQuad) other;
        return topLeft.equals(quad.topLeft)
                && topRight.equals(quad.topRight)
                && bottomRight.equals(quad.bottomRight)
                && bottomLeft.equals(quad.bottomLeft);
    }

    @Override
    public int hashCode() {
        return Objects.hash(topLeft, topRight, bottomRight, bottomLeft);
    }

    String toKey() {
        return topLeft.toKey()
                + ":" + topRight.toKey()
                + ":" + bottomRight.toKey()
                + ":" + bottomLeft.toKey();
    }
}
