package com.desperadoboi.imagetopdf.image;

public final class ExifOrientationMapper {
    private static final int ORIENTATION_UNDEFINED = 0;
    private static final int ORIENTATION_NORMAL = 1;
    private static final int ORIENTATION_FLIP_HORIZONTAL = 2;
    private static final int ORIENTATION_ROTATE_180 = 3;
    private static final int ORIENTATION_FLIP_VERTICAL = 4;
    private static final int ORIENTATION_TRANSPOSE = 5;
    private static final int ORIENTATION_ROTATE_90 = 6;
    private static final int ORIENTATION_TRANSVERSE = 7;
    private static final int ORIENTATION_ROTATE_270 = 8;

    private static final ImageTransform NORMAL = new ImageTransform(0, false);

    private ExifOrientationMapper() {
    }

    public static ImageTransform map(int orientation) {
        switch (orientation) {
            case ORIENTATION_FLIP_HORIZONTAL:
                return new ImageTransform(0, true);
            case ORIENTATION_ROTATE_180:
                return new ImageTransform(180, false);
            case ORIENTATION_FLIP_VERTICAL:
                return new ImageTransform(180, true);
            case ORIENTATION_TRANSPOSE:
                return new ImageTransform(90, true);
            case ORIENTATION_ROTATE_90:
                return new ImageTransform(90, false);
            case ORIENTATION_TRANSVERSE:
                return new ImageTransform(270, true);
            case ORIENTATION_ROTATE_270:
                return new ImageTransform(270, false);
            case ORIENTATION_UNDEFINED:
            case ORIENTATION_NORMAL:
            default:
                return NORMAL;
        }
    }
}
