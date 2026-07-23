package com.desperadoboi.imagetopdf.ui.viewer;

final class SpreadsheetLevelOfDetailPolicy {
    static final float FULL_TEXT_SCALE = 0.60f;
    static final float REDUCED_TEXT_SCALE = 0.40f;
    private static final float MIN_FULL_TEXT_WIDTH_PX = 8f;
    private static final float MIN_FULL_TEXT_HEIGHT_PX = 8f;
    private static final float MIN_REDUCED_TEXT_WIDTH_PX = 32f;
    private static final float MIN_REDUCED_TEXT_HEIGHT_PX = 12f;

    enum Detail {
        FULL,
        REDUCED,
        OVERVIEW
    }

    private SpreadsheetLevelOfDetailPolicy() {
    }

    static Detail detailForScale(float scale) {
        if (scale >= FULL_TEXT_SCALE) return Detail.FULL;
        if (scale >= REDUCED_TEXT_SCALE) return Detail.REDUCED;
        return Detail.OVERVIEW;
    }

    static boolean shouldDrawText(
            float scale,
            float screenWidth,
            float screenHeight,
            boolean wrapText,
            boolean scaling
    ) {
        Detail detail = detailForScale(scale);
        if (detail == Detail.OVERVIEW) return false;
        if (detail == Detail.REDUCED) {
            return !wrapText
                    && screenWidth >= MIN_REDUCED_TEXT_WIDTH_PX
                    && screenHeight >= MIN_REDUCED_TEXT_HEIGHT_PX;
        }
        return screenWidth >= MIN_FULL_TEXT_WIDTH_PX
                && screenHeight >= MIN_FULL_TEXT_HEIGHT_PX;
    }

    static boolean shouldBuildWrappedLayout(
            float scale,
            float screenWidth,
            float screenHeight,
            boolean scaling,
            boolean textNeedsWrap
    ) {
        return detailForScale(scale) == Detail.FULL
                && !scaling
                && textNeedsWrap
                && screenWidth >= MIN_REDUCED_TEXT_WIDTH_PX
                && screenHeight >= MIN_REDUCED_TEXT_HEIGHT_PX;
    }
}
