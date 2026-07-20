package com.desperadoboi.imagetopdf.pdf;

public final class RasterTarget {
    private final int targetWidthPixels;
    private final int targetHeightPixels;

    public RasterTarget(int targetWidthPixels, int targetHeightPixels) {
        if (targetWidthPixels <= 0 || targetHeightPixels <= 0) {
            throw new IllegalArgumentException("Raster target dimensions must be positive");
        }
        this.targetWidthPixels = targetWidthPixels;
        this.targetHeightPixels = targetHeightPixels;
    }

    public int getTargetWidthPixels() {
        return targetWidthPixels;
    }

    public int getTargetHeightPixels() {
        return targetHeightPixels;
    }
}
