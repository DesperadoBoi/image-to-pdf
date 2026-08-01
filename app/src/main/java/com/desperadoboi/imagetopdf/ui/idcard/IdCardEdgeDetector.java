package com.desperadoboi.imagetopdf.ui.idcard;

import android.graphics.Bitmap;

import com.desperadoboi.imagetopdf.model.NormalizedPoint;
import com.desperadoboi.imagetopdf.model.PerspectiveQuad;

public final class IdCardEdgeDetector {
    private static final int GRID_SIZE = 96;
    private static final float ID_CARD_ASPECT = 85.60f / 53.98f;

    private IdCardEdgeDetector() {
    }

    public static PerspectiveQuad detect(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()
                || bitmap.getWidth() < 32 || bitmap.getHeight() < 32) {
            return null;
        }
        int width = Math.min(GRID_SIZE, bitmap.getWidth());
        int height = Math.min(GRID_SIZE, bitmap.getHeight());
        int[] luminance = sampleLuminance(bitmap, width, height);
        float[] columnEnergy = new float[width];
        float[] rowEnergy = new float[height];
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                columnEnergy[x] += Math.abs(
                        luminance[(y * width) + x + 1]
                                - luminance[(y * width) + x - 1]
                );
                rowEnergy[y] += Math.abs(
                        luminance[((y + 1) * width) + x]
                                - luminance[((y - 1) * width) + x]
                );
            }
        }

        int left = strongest(columnEnergy, 2, Math.max(3, width / 2));
        int right = strongest(columnEnergy, Math.min(width - 3, width / 2), width - 2);
        int top = strongest(rowEnergy, 2, Math.max(3, height / 2));
        int bottom = strongest(rowEnergy, Math.min(height - 3, height / 2), height - 2);
        if (left < 0 || right <= left || top < 0 || bottom <= top) {
            return null;
        }
        float normalizedWidth = (right - left) / (float) width;
        float normalizedHeight = (bottom - top) / (float) height;
        float aspect = (normalizedWidth * bitmap.getWidth())
                / (normalizedHeight * bitmap.getHeight());
        if (normalizedWidth < 0.35f
                || normalizedHeight < 0.20f
                || aspect < 1.10f
                || aspect > 2.30f) {
            return null;
        }
        float normalizedLeft = clamp((left / (float) width) - 0.006f);
        float normalizedRight = clamp((right / (float) width) + 0.006f);
        float normalizedTop = clamp((top / (float) height) - 0.006f);
        float normalizedBottom = clamp((bottom / (float) height) + 0.006f);
        try {
            return new PerspectiveQuad(
                    new NormalizedPoint(normalizedLeft, normalizedTop),
                    new NormalizedPoint(normalizedRight, normalizedTop),
                    new NormalizedPoint(normalizedRight, normalizedBottom),
                    new NormalizedPoint(normalizedLeft, normalizedBottom)
            );
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public static PerspectiveQuad defaultQuadFor(int imageWidth, int imageHeight) {
        if (imageWidth <= 0 || imageHeight <= 0) {
            return IdCardImage.DEFAULT_CARD_QUAD;
        }
        float width = 0.88f;
        float height = width * imageWidth / imageHeight / ID_CARD_ASPECT;
        if (height > 0.82f) {
            height = 0.82f;
            width = height * ID_CARD_ASPECT * imageHeight / imageWidth;
        }
        if (width < 0.20f || height < 0.16f || width > 0.94f || height > 0.88f) {
            return IdCardImage.DEFAULT_CARD_QUAD;
        }
        float left = (1f - width) / 2f;
        float right = 1f - left;
        float top = (1f - height) / 2f;
        float bottom = 1f - top;
        return new PerspectiveQuad(
                new NormalizedPoint(left, top),
                new NormalizedPoint(right, top),
                new NormalizedPoint(right, bottom),
                new NormalizedPoint(left, bottom)
        );
    }

    private static int[] sampleLuminance(Bitmap bitmap, int width, int height) {
        int[] values = new int[width * height];
        for (int y = 0; y < height; y++) {
            int sourceY = Math.min(
                    bitmap.getHeight() - 1,
                    (int) ((y + 0.5f) * bitmap.getHeight() / height)
            );
            for (int x = 0; x < width; x++) {
                int sourceX = Math.min(
                        bitmap.getWidth() - 1,
                        (int) ((x + 0.5f) * bitmap.getWidth() / width)
                );
                int color = bitmap.getPixel(sourceX, sourceY);
                int red = (color >> 16) & 0xff;
                int green = (color >> 8) & 0xff;
                int blue = color & 0xff;
                values[(y * width) + x] = (red * 30 + green * 59 + blue * 11) / 100;
            }
        }
        return values;
    }

    private static int strongest(float[] values, int startInclusive, int endExclusive) {
        int best = -1;
        float bestValue = 0f;
        float average = 0f;
        int count = 0;
        for (int index = startInclusive; index < endExclusive; index++) {
            average += values[index];
            count++;
            if (values[index] > bestValue) {
                bestValue = values[index];
                best = index;
            }
        }
        return count == 0 || bestValue < (average / count) * 1.18f ? -1 : best;
    }

    private static float clamp(float value) {
        return Math.max(0.01f, Math.min(0.99f, value));
    }
}
