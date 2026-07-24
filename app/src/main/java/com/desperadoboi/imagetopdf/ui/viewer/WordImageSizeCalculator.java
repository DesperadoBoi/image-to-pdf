package com.desperadoboi.imagetopdf.ui.viewer;

import com.desperadoboi.imagetopdf.document.word.WordMeasurementConverter;

final class WordImageSizeCalculator {
    private WordImageSizeCalculator() {
    }

    static int calculateHeight(
            long declaredWidth,
            long declaredHeight,
            int containerWidth,
            int minimumHeight,
            int maximumHeight
    ) {
        int safeWidth = Math.max(1, containerWidth);
        int safeMinimum = Math.max(1, minimumHeight);
        int safeMaximum = Math.max(safeMinimum, maximumHeight);
        if (declaredWidth > 0L && declaredHeight > 0L) {
            double ratio = declaredHeight / (double) declaredWidth;
            double scaled = safeWidth * ratio;
            if (!Double.isFinite(scaled)) return safeMaximum;
            return Math.max(
                    safeMinimum,
                    Math.min(safeMaximum, (int) Math.round(scaled))
            );
        }
        return Math.min(
                safeMaximum,
                Math.max(safeMinimum, safeWidth * 3 / 4)
        );
    }

    static Size calculate(
            long declaredWidthEmu,
            long declaredHeightEmu,
            int containerWidth,
            int fallbackMinimumHeight,
            int maximumHeight,
            WordMeasurementConverter converter
    ) {
        int safeWidth = Math.max(1, containerWidth);
        int safeMaximumHeight = Math.max(1, maximumHeight);
        float logicalWidth = converter.emuToPixels(declaredWidthEmu);
        float logicalHeight = converter.emuToPixels(declaredHeightEmu);
        if (logicalWidth > 0f && logicalHeight > 0f
                && Float.isFinite(logicalWidth)
                && Float.isFinite(logicalHeight)) {
            float scale = Math.min(
                    1f,
                    Math.min(
                            safeWidth / logicalWidth,
                            safeMaximumHeight / logicalHeight
                    )
            );
            return new Size(
                    Math.max(1, Math.round(logicalWidth * scale)),
                    Math.max(1, Math.round(logicalHeight * scale))
            );
        }
        int fallbackHeight = Math.max(
                Math.max(1, fallbackMinimumHeight),
                safeWidth * 3 / 4
        );
        return new Size(
                safeWidth,
                Math.min(safeMaximumHeight, fallbackHeight)
        );
    }

    static final class Size {
        private final int width;
        private final int height;

        private Size(int width, int height) {
            this.width = width;
            this.height = height;
        }

        int getWidth() { return width; }
        int getHeight() { return height; }
    }
}
