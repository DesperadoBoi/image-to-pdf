package com.desperadoboi.imagetopdf.image;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;

import com.desperadoboi.imagetopdf.model.CropRect;
import com.desperadoboi.imagetopdf.model.NormalizedPoint;
import com.desperadoboi.imagetopdf.model.PageEditSpec;
import com.desperadoboi.imagetopdf.model.PerspectiveQuad;

import java.util.Objects;

public final class PageBitmapProcessor {
    private PageBitmapProcessor() {
    }

    public static Bitmap process(
            Bitmap decodedBitmap,
            ImageTransform exifTransform,
            int manualRotationDegrees,
            PageEditSpec editSpec,
            PageProcessingMode mode
    ) {
        Objects.requireNonNull(decodedBitmap, "decodedBitmap is required");
        Objects.requireNonNull(exifTransform, "exifTransform is required");
        Objects.requireNonNull(editSpec, "editSpec is required");
        Objects.requireNonNull(mode, "mode is required");

        Bitmap current = decodedBitmap;
        try {
            current = ImageBitmapTransformer.applyTransform(current, exifTransform);
            current = ImageBitmapTransformer.applyClockwiseRotation(
                    current,
                    manualRotationDegrees
            );
            if (!mode.appliesPerspective()) {
                return current;
            }
            current = applyPerspective(current, editSpec.getPerspectiveQuad());
            if (!mode.appliesCrop()) {
                return current;
            }
            return applyCrop(current, editSpec.getCropRect());
        } catch (RuntimeException | Error exception) {
            recycle(current);
            throw exception;
        }
    }

    private static Bitmap applyPerspective(Bitmap bitmap, PerspectiveQuad quad) {
        if (quad.isFull()) {
            return bitmap;
        }
        PerspectiveTargetCalculator.Target target = PerspectiveTargetCalculator.calculate(
                bitmap.getWidth(),
                bitmap.getHeight(),
                quad
        );
        Matrix matrix = new Matrix();
        if (!matrix.setPolyToPoly(
                sourcePoints(quad, bitmap.getWidth(), bitmap.getHeight()),
                0,
                target.getDestinationPoints(),
                0,
                4
        )) {
            throw new IllegalArgumentException("Unable to calculate perspective transform");
        }

        Bitmap transformed = Bitmap.createBitmap(
                target.getWidth(),
                target.getHeight(),
                Bitmap.Config.ARGB_8888
        );
        boolean completed = false;
        try {
            Canvas canvas = new Canvas(transformed);
            canvas.drawColor(Color.TRANSPARENT);
            canvas.drawBitmap(
                    bitmap,
                    matrix,
                    new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG)
            );
            completed = true;
        } finally {
            if (!completed) {
                recycle(transformed);
            }
        }
        recycle(bitmap);
        return transformed;
    }

    private static Bitmap applyCrop(Bitmap bitmap, CropRect cropRect) {
        if (cropRect.isFull()) {
            return bitmap;
        }
        int left = clamp((int) Math.floor(cropRect.getLeft() * bitmap.getWidth()), 0, bitmap.getWidth() - 1);
        int top = clamp((int) Math.floor(cropRect.getTop() * bitmap.getHeight()), 0, bitmap.getHeight() - 1);
        int right = clamp((int) Math.ceil(cropRect.getRight() * bitmap.getWidth()), left + 1, bitmap.getWidth());
        int bottom = clamp((int) Math.ceil(cropRect.getBottom() * bitmap.getHeight()), top + 1, bitmap.getHeight());
        Bitmap cropped = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top);
        if (cropped != bitmap) {
            recycle(bitmap);
        }
        return cropped;
    }

    private static float[] sourcePoints(PerspectiveQuad quad, int width, int height) {
        return new float[]{
                x(quad.getTopLeft(), width), y(quad.getTopLeft(), height),
                x(quad.getTopRight(), width), y(quad.getTopRight(), height),
                x(quad.getBottomRight(), width), y(quad.getBottomRight(), height),
                x(quad.getBottomLeft(), width), y(quad.getBottomLeft(), height)
        };
    }

    private static float x(NormalizedPoint point, int width) {
        return point.getX() * width;
    }

    private static float y(NormalizedPoint point, int height) {
        return point.getY() * height;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static void recycle(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }

}
