package com.desperadoboi.imagetopdf.ui.watermark;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class WatermarkGeometry {
    public static final float EDGE_PADDING_POINTS = 18f;
    private static final int MAX_TILED_PLACEMENTS = 256;

    private WatermarkGeometry() {
    }

    public static Layout calculate(
            float pageWidth,
            float pageHeight,
            float textWidth,
            float textHeight,
            float rotationDegrees,
            WatermarkStyle style,
            WatermarkPosition position
    ) {
        if (!isPositiveFinite(pageWidth) || !isPositiveFinite(pageHeight)
                || !isPositiveFinite(textWidth) || !isPositiveFinite(textHeight)) {
            return Layout.empty();
        }
        float padding = Math.min(
                EDGE_PADDING_POINTS,
                Math.min(pageWidth, pageHeight) / 8f
        );
        Bounds rawBounds = rotatedBounds(textWidth, textHeight, rotationDegrees);
        float availableWidth = Math.max(1f, pageWidth - padding * 2f);
        float availableHeight = Math.max(1f, pageHeight - padding * 2f);
        float scale = Math.min(
                1f,
                Math.min(
                        availableWidth / rawBounds.width,
                        availableHeight / rawBounds.height
                )
        );
        if (scale < 1f) scale *= 0.98f;

        Bounds bounds = rotatedBounds(
                textWidth * scale,
                textHeight * scale,
                rotationDegrees
        );
        if (style == WatermarkStyle.TILED) {
            return tiled(pageWidth, pageHeight, padding, scale, bounds);
        }
        return single(
                pageWidth,
                pageHeight,
                padding,
                scale,
                bounds,
                position == null ? WatermarkPosition.CENTER : position
        );
    }

    public static Bounds rotatedBounds(float width, float height, float rotationDegrees) {
        double radians = Math.toRadians(rotationDegrees);
        float cosine = (float) Math.abs(Math.cos(radians));
        float sine = (float) Math.abs(Math.sin(radians));
        return new Bounds(
                width * cosine + height * sine,
                width * sine + height * cosine
        );
    }

    private static Layout single(
            float pageWidth,
            float pageHeight,
            float padding,
            float scale,
            Bounds bounds,
            WatermarkPosition position
    ) {
        float halfWidth = bounds.width / 2f;
        float halfHeight = bounds.height / 2f;
        float centerX = axisPosition(
                position.getColumn(),
                padding + halfWidth,
                pageWidth / 2f,
                pageWidth - padding - halfWidth
        );
        float centerY = axisPosition(
                position.getRow(),
                padding + halfHeight,
                pageHeight / 2f,
                pageHeight - padding - halfHeight
        );
        return new Layout(
                scale,
                bounds,
                padding,
                Collections.singletonList(new Placement(centerX, centerY))
        );
    }

    private static Layout tiled(
            float pageWidth,
            float pageHeight,
            float padding,
            float scale,
            Bounds bounds
    ) {
        float halfWidth = bounds.width / 2f;
        float halfHeight = bounds.height / 2f;
        float minX = padding + halfWidth;
        float maxX = pageWidth - padding - halfWidth;
        float minY = padding + halfHeight;
        float maxY = pageHeight - padding - halfHeight;
        float stepX = Math.max(bounds.width * 1.25f, 90f);
        float stepY = Math.max(bounds.height * 1.25f, 76f);
        ArrayList<Placement> placements = new ArrayList<>();
        int row = 0;
        for (float y = minY; y <= maxY + 0.01f
                && placements.size() < MAX_TILED_PLACEMENTS; y += stepY) {
            float offset = (row & 1) == 0 ? 0f : stepX / 2f;
            for (float x = minX + offset; x <= maxX + 0.01f
                    && placements.size() < MAX_TILED_PLACEMENTS; x += stepX) {
                placements.add(new Placement(x, y));
            }
            row++;
        }
        if (placements.isEmpty()) {
            placements.add(new Placement(pageWidth / 2f, pageHeight / 2f));
        }
        return new Layout(scale, bounds, padding, placements);
    }

    private static float axisPosition(int index, float start, float center, float end) {
        if (index <= 0) return start;
        if (index >= 2) return end;
        return center;
    }

    private static boolean isPositiveFinite(float value) {
        return Float.isFinite(value) && value > 0f;
    }

    public static final class Bounds {
        private final float width;
        private final float height;

        private Bounds(float width, float height) {
            this.width = width;
            this.height = height;
        }

        public float getWidth() { return width; }
        public float getHeight() { return height; }
    }

    public static final class Placement {
        private final float centerX;
        private final float centerY;

        private Placement(float centerX, float centerY) {
            this.centerX = centerX;
            this.centerY = centerY;
        }

        public float getCenterX() { return centerX; }
        public float getCenterY() { return centerY; }
    }

    public static final class Layout {
        private final float textScale;
        private final Bounds rotatedBounds;
        private final float edgePadding;
        private final List<Placement> placements;

        private Layout(
                float textScale,
                Bounds rotatedBounds,
                float edgePadding,
                List<Placement> placements
        ) {
            this.textScale = textScale;
            this.rotatedBounds = rotatedBounds;
            this.edgePadding = edgePadding;
            this.placements = Collections.unmodifiableList(new ArrayList<>(placements));
        }

        private static Layout empty() {
            return new Layout(1f, new Bounds(0f, 0f), 0f, Collections.emptyList());
        }

        public float getTextScale() { return textScale; }
        public Bounds getRotatedBounds() { return rotatedBounds; }
        public float getEdgePadding() { return edgePadding; }
        public List<Placement> getPlacements() { return placements; }
    }
}
