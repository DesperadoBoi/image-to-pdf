package com.desperadoboi.imagetopdf.image;

import com.desperadoboi.imagetopdf.model.PageEditSpec;

import java.util.Objects;

public final class EditedImageGeometryCalculator {
    private EditedImageGeometryCalculator() {
    }

    public static Dimensions calculate(
            int orientedSourceWidth,
            int orientedSourceHeight,
            PageEditSpec editSpec,
            PageProcessingMode mode
    ) {
        if (orientedSourceWidth <= 0 || orientedSourceHeight <= 0) {
            throw new IllegalArgumentException("Source dimensions must be positive");
        }
        Objects.requireNonNull(editSpec, "editSpec is required");
        Objects.requireNonNull(mode, "mode is required");

        double outputWidth = orientedSourceWidth;
        double outputHeight = orientedSourceHeight;
        if (mode.appliesPerspective() && !editSpec.getPerspectiveQuad().isFull()) {
            PerspectiveTargetCalculator.Geometry geometry =
                    PerspectiveTargetCalculator.measureUnbounded(
                            orientedSourceWidth,
                            orientedSourceHeight,
                            editSpec.getPerspectiveQuad()
                    );
            outputWidth = geometry.getWidth();
            outputHeight = geometry.getHeight();
        }
        if (mode.appliesCrop()) {
            outputWidth *= editSpec.getCropRect().getWidth();
            outputHeight *= editSpec.getCropRect().getHeight();
        }
        return Dimensions.fromPositiveDoubles(outputWidth, outputHeight);
    }

    public static final class Dimensions {
        private final int width;
        private final int height;

        public Dimensions(int width, int height) {
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException("Dimensions must be positive");
            }
            this.width = width;
            this.height = height;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }

        private static Dimensions fromPositiveDoubles(double width, double height) {
            if (!Double.isFinite(width)
                    || !Double.isFinite(height)
                    || width <= 0d
                    || height <= 0d) {
                throw new IllegalArgumentException("Calculated dimensions must be positive");
            }
            double scale = Math.min(
                    1d,
                    Math.min(Integer.MAX_VALUE / width, Integer.MAX_VALUE / height)
            );
            return new Dimensions(
                    Math.max(1, (int) Math.ceil(width * scale)),
                    Math.max(1, (int) Math.ceil(height * scale))
            );
        }
    }
}
