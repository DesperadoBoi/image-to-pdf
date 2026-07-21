package com.desperadoboi.imagetopdf.image;

import com.desperadoboi.imagetopdf.model.PageEditSpec;

import java.util.Objects;

public final class SourceResolutionCalculator {
    private SourceResolutionCalculator() {
    }

    public static EditedImageGeometryCalculator.Dimensions calculateForFitTarget(
            int orientedSourceWidth,
            int orientedSourceHeight,
            PageEditSpec editSpec,
            PageProcessingMode mode,
            int targetWidth,
            int targetHeight
    ) {
        return calculate(
                orientedSourceWidth,
                orientedSourceHeight,
                editSpec,
                mode,
                targetWidth,
                targetHeight,
                true
        );
    }

    public static EditedImageGeometryCalculator.Dimensions calculateForOutputTarget(
            int orientedSourceWidth,
            int orientedSourceHeight,
            PageEditSpec editSpec,
            PageProcessingMode mode,
            int targetWidth,
            int targetHeight
    ) {
        return calculate(
                orientedSourceWidth,
                orientedSourceHeight,
                editSpec,
                mode,
                targetWidth,
                targetHeight,
                false
        );
    }

    private static EditedImageGeometryCalculator.Dimensions calculate(
            int orientedSourceWidth,
            int orientedSourceHeight,
            PageEditSpec editSpec,
            PageProcessingMode mode,
            int targetWidth,
            int targetHeight,
            boolean fitTarget
    ) {
        if (orientedSourceWidth <= 0 || orientedSourceHeight <= 0) {
            throw new IllegalArgumentException("Source dimensions must be positive");
        }
        if (targetWidth <= 0 || targetHeight <= 0) {
            throw new IllegalArgumentException("Target dimensions must be positive");
        }
        Objects.requireNonNull(editSpec, "editSpec is required");
        Objects.requireNonNull(mode, "mode is required");
        EditedImageGeometryCalculator.Dimensions output =
                EditedImageGeometryCalculator.calculate(
                        orientedSourceWidth,
                        orientedSourceHeight,
                        editSpec,
                        mode
                );
        double widthScale = targetWidth / (double) output.getWidth();
        double heightScale = targetHeight / (double) output.getHeight();
        double requiredScale = fitTarget
                ? Math.min(widthScale, heightScale)
                : Math.max(widthScale, heightScale);
        requiredScale = Math.min(1d, Math.max(0d, requiredScale));
        return new EditedImageGeometryCalculator.Dimensions(
                Math.max(1, (int) Math.ceil(orientedSourceWidth * requiredScale)),
                Math.max(1, (int) Math.ceil(orientedSourceHeight * requiredScale))
        );
    }
}
