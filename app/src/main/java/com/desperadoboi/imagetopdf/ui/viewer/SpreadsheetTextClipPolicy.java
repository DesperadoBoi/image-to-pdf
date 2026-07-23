package com.desperadoboi.imagetopdf.ui.viewer;

final class SpreadsheetTextClipPolicy {
    private SpreadsheetTextClipPolicy() {
    }

    static float contentStart(float cellStart, float cellEnd, float padding) {
        return Math.min(cellEnd, cellStart + Math.max(0f, padding));
    }

    static float contentEnd(float cellStart, float cellEnd, float padding) {
        return Math.max(cellStart, cellEnd - Math.max(0f, padding));
    }

    static boolean isDrawable(float start, float end) {
        return end > start;
    }
}
