package com.desperadoboi.imagetopdf.pdf;

import com.desperadoboi.imagetopdf.model.ImagePlacementMode;
import com.desperadoboi.imagetopdf.util.ImagePlacementCalculator;

import java.util.Objects;

public final class RasterTargetCalculator {
    public static final int TARGET_DPI = 144;

    private static final int PDF_POINTS_PER_INCH = 72;

    private RasterTargetCalculator() {
    }

    public static RasterTarget calculate(
            int orientedSourceWidth,
            int orientedSourceHeight,
            float contentLeft,
            float contentTop,
            float contentWidth,
            float contentHeight,
            ImagePlacementMode placementMode
    ) {
        return calculate(
                orientedSourceWidth,
                orientedSourceHeight,
                contentLeft,
                contentTop,
                contentWidth,
                contentHeight,
                placementMode,
                TARGET_DPI
        );
    }

    public static RasterTarget calculate(
            int orientedSourceWidth,
            int orientedSourceHeight,
            float contentLeft,
            float contentTop,
            float contentWidth,
            float contentHeight,
            ImagePlacementMode placementMode,
            int targetDpi
    ) {
        Objects.requireNonNull(placementMode, "placementMode is required");
        validateSourceDimensions(orientedSourceWidth, orientedSourceHeight);
        if (targetDpi <= 0) {
            throw new IllegalArgumentException("targetDpi must be positive");
        }

        ImagePlacementCalculator.ImageDrawPlan drawPlan =
                ImagePlacementCalculator.calculateDrawPlan(
                        orientedSourceWidth,
                        orientedSourceHeight,
                        contentLeft,
                        contentTop,
                        contentWidth,
                        contentHeight,
                        placementMode
                );
        ImagePlacementCalculator.PlacementRect destination = drawPlan.getDestinationRect();
        long destinationTargetWidth = pointsToPixelsCeil(destination.getWidth(), targetDpi);
        long destinationTargetHeight = pointsToPixelsCeil(destination.getHeight(), targetDpi);

        if (placementMode == ImagePlacementMode.FILL) {
            return calculateFillTarget(
                    orientedSourceWidth,
                    orientedSourceHeight,
                    destination.getWidth(),
                    destination.getHeight(),
                    destinationTargetWidth,
                    destinationTargetHeight
            );
        }

        return clampToSourceAspect(
                destinationTargetWidth,
                destinationTargetHeight,
                orientedSourceWidth,
                orientedSourceHeight
        );
    }

    public static RasterTarget calculate(
            int sourceWidth,
            int sourceHeight,
            boolean exifSwapsDimensions,
            int manualRotationDegrees,
            float contentLeft,
            float contentTop,
            float contentWidth,
            float contentHeight,
            ImagePlacementMode placementMode
    ) {
        validateRotation(manualRotationDegrees);
        boolean swapsDimensions = exifSwapsDimensions
                ^ (manualRotationDegrees == 90 || manualRotationDegrees == 270);
        return calculate(
                swapsDimensions ? sourceHeight : sourceWidth,
                swapsDimensions ? sourceWidth : sourceHeight,
                contentLeft,
                contentTop,
                contentWidth,
                contentHeight,
                placementMode
        );
    }

    private static RasterTarget calculateFillTarget(
            int sourceWidth,
            int sourceHeight,
            float destinationWidthPoints,
            float destinationHeightPoints,
            long destinationTargetWidth,
            long destinationTargetHeight
    ) {
        double sourceAspectRatio = sourceWidth / (double) sourceHeight;
        double destinationAspectRatio = destinationWidthPoints / (double) destinationHeightPoints;

        long targetWidth;
        long targetHeight;
        if (sourceAspectRatio > destinationAspectRatio) {
            targetHeight = Math.max(
                    destinationTargetHeight,
                    ceilDivide(destinationTargetWidth, destinationAspectRatio)
            );
            targetWidth = ceilMultiply(targetHeight, sourceAspectRatio);
        } else if (sourceAspectRatio < destinationAspectRatio) {
            targetWidth = Math.max(
                    destinationTargetWidth,
                    ceilMultiply(destinationTargetHeight, destinationAspectRatio)
            );
            targetHeight = ceilDivide(targetWidth, sourceAspectRatio);
        } else {
            targetWidth = destinationTargetWidth;
            targetHeight = destinationTargetHeight;
        }

        return clampToSourceAspect(targetWidth, targetHeight, sourceWidth, sourceHeight);
    }

    private static RasterTarget clampToSourceAspect(
            long desiredWidth,
            long desiredHeight,
            int sourceWidth,
            int sourceHeight
    ) {
        validateSourceDimensions(sourceWidth, sourceHeight);
        if (desiredWidth <= 0L || desiredHeight <= 0L) {
            throw new IllegalArgumentException("Desired raster dimensions must be positive");
        }
        if (sourceWidth <= desiredWidth && sourceHeight <= desiredHeight) {
            return new RasterTarget(sourceWidth, sourceHeight);
        }

        double scale = Math.min(
                1d,
                Math.min(sourceWidth / (double) desiredWidth, sourceHeight / (double) desiredHeight)
        );
        long scaledWidth = Math.max(1L, (long) Math.floor(desiredWidth * scale));
        long scaledHeight = Math.max(1L, (long) Math.floor(desiredHeight * scale));

        return new RasterTarget(
                safePositiveInt(Math.min(sourceWidth, scaledWidth)),
                safePositiveInt(Math.min(sourceHeight, scaledHeight))
        );
    }

    private static long pointsToPixelsCeil(float points, int targetDpi) {
        if (!Float.isFinite(points) || points <= 0f) {
            throw new IllegalArgumentException("Destination dimensions must be positive");
        }
        return ceilMultiply(points, targetDpi / (double) PDF_POINTS_PER_INCH);
    }

    private static long ceilMultiply(double value, double multiplier) {
        double result = value * multiplier;
        if (!Double.isFinite(result) || result > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return Math.max(1L, (long) Math.ceil(result));
    }

    private static long ceilDivide(double value, double divisor) {
        if (!Double.isFinite(divisor) || divisor <= 0d) {
            throw new IllegalArgumentException("Aspect ratio must be positive");
        }
        double result = value / divisor;
        if (!Double.isFinite(result) || result > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return Math.max(1L, (long) Math.ceil(result));
    }

    private static int safePositiveInt(long value) {
        if (value <= 0L) {
            return 1;
        }
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) value;
    }

    private static void validateSourceDimensions(int sourceWidth, int sourceHeight) {
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            throw new IllegalArgumentException("Source dimensions must be positive");
        }
    }

    private static void validateRotation(int rotationDegrees) {
        if (rotationDegrees != 0
                && rotationDegrees != 90
                && rotationDegrees != 180
                && rotationDegrees != 270) {
            throw new IllegalArgumentException("Rotation must be 0, 90, 180, or 270 degrees");
        }
    }
}
