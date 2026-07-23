package com.desperadoboi.imagetopdf.ui.viewer;

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
}
