package com.desperadoboi.imagetopdf.util;

import com.desperadoboi.imagetopdf.model.ImagePlacementMode;

import java.util.Objects;

public final class ImagePlacementCalculator {
    private ImagePlacementCalculator() {
    }

    public static ImageDrawPlan calculateDrawPlan(
            int imageWidth,
            int imageHeight,
            float containerLeft,
            float containerTop,
            float containerWidth,
            float containerHeight,
            ImagePlacementMode placementMode
    ) {
        Objects.requireNonNull(placementMode, "placementMode is required");
        validateInput(imageWidth, imageHeight, containerLeft, containerTop, containerWidth, containerHeight);

        if (placementMode == ImagePlacementMode.FILL) {
            return calculateCenteredFill(
                    imageWidth,
                    imageHeight,
                    containerLeft,
                    containerTop,
                    containerWidth,
                    containerHeight
            );
        }

        PlacementRect destination = calculateCenteredFit(
                imageWidth,
                imageHeight,
                containerLeft,
                containerTop,
                containerWidth,
                containerHeight
        );
        return new ImageDrawPlan(
                new PlacementRect(0f, 0f, imageWidth, imageHeight),
                destination
        );
    }

    public static PlacementRect calculateCenteredFit(
            int imageWidth,
            int imageHeight,
            float containerLeft,
            float containerTop,
            float containerWidth,
            float containerHeight
    ) {
        validateInput(imageWidth, imageHeight, containerLeft, containerTop, containerWidth, containerHeight);

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

    private static ImageDrawPlan calculateCenteredFill(
            int imageWidth,
            int imageHeight,
            float containerLeft,
            float containerTop,
            float containerWidth,
            float containerHeight
    ) {
        float imageAspectRatio = imageWidth / (float) imageHeight;
        float containerAspectRatio = containerWidth / containerHeight;

        float sourceLeft = 0f;
        float sourceTop = 0f;
        float sourceRight = imageWidth;
        float sourceBottom = imageHeight;
        if (imageAspectRatio > containerAspectRatio) {
            float sourceWidth = imageHeight * containerAspectRatio;
            sourceLeft = (imageWidth - sourceWidth) / 2f;
            sourceRight = sourceLeft + sourceWidth;
        } else if (imageAspectRatio < containerAspectRatio) {
            float sourceHeight = imageWidth / containerAspectRatio;
            sourceTop = (imageHeight - sourceHeight) / 2f;
            sourceBottom = sourceTop + sourceHeight;
        }

        return new ImageDrawPlan(
                new PlacementRect(sourceLeft, sourceTop, sourceRight, sourceBottom),
                new PlacementRect(
                        containerLeft,
                        containerTop,
                        containerLeft + containerWidth,
                        containerTop + containerHeight
                )
        );
    }

    private static void validateInput(
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
        if (containerLeft < 0f || containerTop < 0f) {
            throw new IllegalArgumentException("Container origin must not be negative");
        }
        if (containerWidth <= 0f || containerHeight <= 0f) {
            throw new IllegalArgumentException("Container dimensions must be positive");
        }
    }

    public static final class ImageDrawPlan {
        private final PlacementRect sourceRect;
        private final PlacementRect destinationRect;

        public ImageDrawPlan(PlacementRect sourceRect, PlacementRect destinationRect) {
            this.sourceRect = Objects.requireNonNull(sourceRect, "sourceRect is required");
            this.destinationRect = Objects.requireNonNull(
                    destinationRect,
                    "destinationRect is required"
            );
        }

        public PlacementRect getSourceRect() {
            return sourceRect;
        }

        public PlacementRect getDestinationRect() {
            return destinationRect;
        }
    }

    public static final class PlacementRect {
        private final float left;
        private final float top;
        private final float right;
        private final float bottom;

        public PlacementRect(float left, float top, float right, float bottom) {
            if (left < 0f || top < 0f || right < 0f || bottom < 0f) {
                throw new IllegalArgumentException("Rect bounds must not be negative");
            }
            if (right <= left || bottom <= top) {
                throw new IllegalArgumentException("Rect area must be positive");
            }
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
