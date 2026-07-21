package com.desperadoboi.imagetopdf.model;

public enum PdfQualityProfile {
    COMPACT(96),
    BALANCED(144),
    HIGH(216);

    private final int targetDpi;

    PdfQualityProfile(int targetDpi) {
        this.targetDpi = targetDpi;
    }

    public int getTargetDpi() {
        return targetDpi;
    }
}
