package com.desperadoboi.imagetopdf.util;

public final class ImagePlacementCalculator {
    private ImagePlacementCalculator() {
    }

    public static PlacementRect calculateCenteredFit(
            int imageWidth,
            int imageHeight,
            float containerLeft,
            float containerTop,
            float containerWidth,
            float containerHeight
    ) {
        if (imageWidth <= 0 || imageHeight <= 0) {
            throw new IllegalArgumentException("Image dimensions must be positive");
        }
        if (containerWidth <= 0f || containerHeight <= 0f) {
            throw new IllegalArgumentException("Container dimensions must be positive");
        }

        float scale = Math.min(containerWidth / imageWidth, containerHeight / imageHeight);
        float fittedWidth = imageWidth * scale;
        float fittedHeight = imageHeight * scale;
        float left = containerLeft + ((containerWidth - fittedWidth) / 2f);
        float top = containerTop + ((containerHeight - fittedHeight) / 2f);

        return new PlacementRect(
                left,
                top,
                left + fittedWidth,
                top + fittedHeight
        );
    }

    public static final class PlacementRect {
        private final float left;
        private final float top;
        private final float right;
        private final float bottom;

        public PlacementRect(float left, float top, float right, float bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
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
    }
}
