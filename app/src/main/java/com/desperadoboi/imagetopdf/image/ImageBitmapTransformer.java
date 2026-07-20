package com.desperadoboi.imagetopdf.image;

import android.graphics.Bitmap;
import android.graphics.Matrix;

public final class ImageBitmapTransformer {
    private ImageBitmapTransformer() {
    }

    public static Bitmap applyTransform(Bitmap bitmap, ImageTransform imageTransform) {
        if (imageTransform.isIdentity()) {
            return bitmap;
        }

        Matrix matrix = new Matrix();
        matrix.setRotate(imageTransform.getRotationDegrees());
        if (imageTransform.shouldFlipHorizontally()) {
            matrix.postScale(-1f, 1f);
        }
        return createTransformedBitmap(bitmap, matrix);
    }

    public static Bitmap applyClockwiseRotation(Bitmap bitmap, int rotationDegrees) {
        if (rotationDegrees == 0) {
            return bitmap;
        }
        if (rotationDegrees != 90 && rotationDegrees != 180 && rotationDegrees != 270) {
            throw new IllegalArgumentException("Rotation must be 0, 90, 180, or 270 degrees");
        }

        Matrix matrix = new Matrix();
        matrix.setRotate(rotationDegrees);
        return createTransformedBitmap(bitmap, matrix);
    }

    public static Bitmap scaleDownToFit(Bitmap bitmap, int targetWidth, int targetHeight) {
        if (targetWidth <= 0 || targetHeight <= 0) {
            throw new IllegalArgumentException("Target dimensions must be positive");
        }
        if (bitmap.getWidth() <= targetWidth && bitmap.getHeight() <= targetHeight) {
            return bitmap;
        }

        double scale = Math.min(
                targetWidth / (double) bitmap.getWidth(),
                targetHeight / (double) bitmap.getHeight()
        );
        if (scale >= 1d) {
            return bitmap;
        }

        int scaledWidth = Math.max(1, (int) Math.floor(bitmap.getWidth() * scale));
        int scaledHeight = Math.max(1, (int) Math.floor(bitmap.getHeight() * scale));
        if (scaledWidth == bitmap.getWidth() && scaledHeight == bitmap.getHeight()) {
            return bitmap;
        }

        Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true);
        if (scaledBitmap != bitmap && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
        return scaledBitmap;
    }

    private static Bitmap createTransformedBitmap(Bitmap bitmap, Matrix matrix) {
        Bitmap transformedBitmap = Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.getWidth(),
                bitmap.getHeight(),
                matrix,
                true
        );
        if (transformedBitmap != bitmap && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
        return transformedBitmap;
    }
}
