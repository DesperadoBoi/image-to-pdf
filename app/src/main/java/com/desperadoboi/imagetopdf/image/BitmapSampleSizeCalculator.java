package com.desperadoboi.imagetopdf.image;

public final class BitmapSampleSizeCalculator {
    private BitmapSampleSizeCalculator() {
    }

    public static int calculate(
            int sourceWidth,
            int sourceHeight,
            int targetWidth,
            int targetHeight
    ) {
        if (sourceWidth <= 0 || sourceHeight <= 0 || targetWidth <= 0 || targetHeight <= 0) {
            throw new IllegalArgumentException("Bitmap dimensions must be positive");
        }
        int sampleSize = 1;
        while (sampleSize <= Integer.MAX_VALUE / 2) {
            int nextSampleSize = sampleSize * 2;
            if ((sourceWidth / nextSampleSize) < targetWidth
                    || (sourceHeight / nextSampleSize) < targetHeight) {
                break;
            }
            sampleSize = nextSampleSize;
        }
        return sampleSize;
    }
}
