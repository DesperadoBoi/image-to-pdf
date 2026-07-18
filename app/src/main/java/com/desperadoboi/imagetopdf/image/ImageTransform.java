package com.desperadoboi.imagetopdf.image;

public final class ImageTransform {
    private final int rotationDegrees;
    private final boolean flipHorizontally;

    public ImageTransform(int rotationDegrees, boolean flipHorizontally) {
        if (rotationDegrees != 0
                && rotationDegrees != 90
                && rotationDegrees != 180
                && rotationDegrees != 270) {
            throw new IllegalArgumentException("Rotation must be 0, 90, 180, or 270 degrees");
        }

        this.rotationDegrees = rotationDegrees;
        this.flipHorizontally = flipHorizontally;
    }

    public int getRotationDegrees() {
        return rotationDegrees;
    }

    public boolean shouldFlipHorizontally() {
        return flipHorizontally;
    }

    public boolean swapsDimensions() {
        return rotationDegrees == 90 || rotationDegrees == 270;
    }

    public boolean isIdentity() {
        return rotationDegrees == 0 && !flipHorizontally;
    }
}
