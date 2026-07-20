package com.desperadoboi.imagetopdf.image;

import com.desperadoboi.imagetopdf.model.NormalizedPoint;
import com.desperadoboi.imagetopdf.model.PerspectiveQuad;

import java.util.Objects;

public final class PerspectiveTargetCalculator {
    private static final int MAX_TARGET_EDGE = 8192;
    private static final long MAX_TARGET_PIXELS = 16_000_000L;

    private PerspectiveTargetCalculator() {
    }

    public static Target calculate(int sourceWidth, int sourceHeight, PerspectiveQuad quad) {
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            throw new IllegalArgumentException("Source dimensions must be positive");
        }
        Objects.requireNonNull(quad, "quad is required");

        double top = edgeLength(quad.getTopLeft(), quad.getTopRight(), sourceWidth, sourceHeight);
        double bottom = edgeLength(
                quad.getBottomLeft(),
                quad.getBottomRight(),
                sourceWidth,
                sourceHeight
        );
        double left = edgeLength(
                quad.getTopLeft(),
                quad.getBottomLeft(),
                sourceWidth,
                sourceHeight
        );
        double right = edgeLength(
                quad.getTopRight(),
                quad.getBottomRight(),
                sourceWidth,
                sourceHeight
        );
        long targetWidth = safeCeil(Math.max(top, bottom));
        long targetHeight = safeCeil(Math.max(left, right));
        targetWidth = Math.min(targetWidth, MAX_TARGET_EDGE);
        targetHeight = Math.min(targetHeight, MAX_TARGET_EDGE);

        long sourcePixels = (long) sourceWidth * sourceHeight;
        long allowedPixels = Math.max(1L, Math.min(sourcePixels, MAX_TARGET_PIXELS));
        double targetPixels = targetWidth * (double) targetHeight;
        if (targetPixels > allowedPixels) {
            double scale = Math.sqrt(allowedPixels / targetPixels);
            targetWidth = Math.max(1L, (long) Math.floor(targetWidth * scale));
            targetHeight = Math.max(1L, (long) Math.floor(targetHeight * scale));
        }
        return new Target(safeInt(targetWidth), safeInt(targetHeight));
    }

    private static double edgeLength(
            NormalizedPoint first,
            NormalizedPoint second,
            int width,
            int height
    ) {
        double dx = (second.getX() - first.getX()) * width;
        double dy = (second.getY() - first.getY()) * height;
        return Math.hypot(dx, dy);
    }

    private static long safeCeil(double value) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException("Perspective target edge must be positive");
        }
        return Math.max(1L, Math.min(Integer.MAX_VALUE, (long) Math.ceil(value)));
    }

    private static int safeInt(long value) {
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, value));
    }

    public static final class Target {
        private final int width;
        private final int height;

        private Target(int width, int height) {
            this.width = width;
            this.height = height;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }

        public float[] getDestinationPoints() {
            return new float[]{0f, 0f, width, 0f, width, height, 0f, height};
        }
    }
}
