package com.desperadoboi.imagetopdf.ui.viewer;

final class SpreadsheetAxisOffsets {
    private float horizontal;
    private float vertical;

    void setHorizontal(float horizontal) {
        this.horizontal = Math.max(0f, horizontal);
    }

    void setVertical(float vertical) {
        this.vertical = Math.max(0f, vertical);
    }

    float getBodyHorizontal() {
        return horizontal;
    }

    float getBodyVertical() {
        return vertical;
    }

    float getColumnHeaderHorizontal() {
        return horizontal;
    }

    float getColumnHeaderVertical() {
        return 0f;
    }

    float getRowHeaderHorizontal() {
        return 0f;
    }

    float getRowHeaderVertical() {
        return vertical;
    }
}
