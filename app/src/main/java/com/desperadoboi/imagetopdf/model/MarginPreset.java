package com.desperadoboi.imagetopdf.model;

public enum MarginPreset {
    NONE(0),
    SMALL(12),
    STANDARD(24);

    private final int marginPoints;

    MarginPreset(int marginPoints) {
        this.marginPoints = marginPoints;
    }

    public int getMarginPoints() {
        return marginPoints;
    }
}
