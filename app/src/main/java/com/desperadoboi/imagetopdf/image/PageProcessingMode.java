package com.desperadoboi.imagetopdf.image;

public enum PageProcessingMode {
    FINAL(true, true),
    BEFORE_CROP(true, false),
    ORIENTED_ONLY(false, false);

    private final boolean appliesPerspective;
    private final boolean appliesCrop;

    PageProcessingMode(boolean appliesPerspective, boolean appliesCrop) {
        this.appliesPerspective = appliesPerspective;
        this.appliesCrop = appliesCrop;
    }

    public boolean appliesPerspective() {
        return appliesPerspective;
    }

    public boolean appliesCrop() {
        return appliesCrop;
    }
}
