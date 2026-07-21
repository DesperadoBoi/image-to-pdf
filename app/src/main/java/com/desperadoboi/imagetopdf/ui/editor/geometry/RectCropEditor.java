package com.desperadoboi.imagetopdf.ui.editor.geometry;

import com.desperadoboi.imagetopdf.model.CropRect;

import java.util.Objects;

public final class RectCropEditor {
    private RectCropEditor() {
    }

    public static CropRect moveHandle(
            CropRect cropRect,
            Handle handle,
            float targetX,
            float targetY,
            float minimumWidth,
            float minimumHeight
    ) {
        Objects.requireNonNull(cropRect, "cropRect is required");
        Objects.requireNonNull(handle, "handle is required");
        validateMinimumSize(minimumWidth, minimumHeight);
        if (!Float.isFinite(targetX) || !Float.isFinite(targetY)) {
            return cropRect;
        }

        float left = cropRect.getLeft();
        float top = cropRect.getTop();
        float right = cropRect.getRight();
        float bottom = cropRect.getBottom();
        switch (handle) {
            case TOP_LEFT:
                left = clamp(targetX, 0f, right - minimumWidth);
                top = clamp(targetY, 0f, bottom - minimumHeight);
                break;
            case TOP_CENTER:
                top = clamp(targetY, 0f, bottom - minimumHeight);
                break;
            case TOP_RIGHT:
                right = clamp(targetX, left + minimumWidth, 1f);
                top = clamp(targetY, 0f, bottom - minimumHeight);
                break;
            case CENTER_RIGHT:
                right = clamp(targetX, left + minimumWidth, 1f);
                break;
            case BOTTOM_RIGHT:
                right = clamp(targetX, left + minimumWidth, 1f);
                bottom = clamp(targetY, top + minimumHeight, 1f);
                break;
            case BOTTOM_CENTER:
                bottom = clamp(targetY, top + minimumHeight, 1f);
                break;
            case BOTTOM_LEFT:
                left = clamp(targetX, 0f, right - minimumWidth);
                bottom = clamp(targetY, top + minimumHeight, 1f);
                break;
            case CENTER_LEFT:
                left = clamp(targetX, 0f, right - minimumWidth);
                break;
            default:
                throw new IllegalArgumentException("MOVE is not a resize handle");
        }
        return new CropRect(left, top, right, bottom);
    }

    public static CropRect move(CropRect cropRect, float deltaX, float deltaY) {
        Objects.requireNonNull(cropRect, "cropRect is required");
        if (!Float.isFinite(deltaX) || !Float.isFinite(deltaY)) {
            return cropRect;
        }
        float clampedDeltaX = clamp(deltaX, -cropRect.getLeft(), 1f - cropRect.getRight());
        float clampedDeltaY = clamp(deltaY, -cropRect.getTop(), 1f - cropRect.getBottom());
        return new CropRect(
                cropRect.getLeft() + clampedDeltaX,
                cropRect.getTop() + clampedDeltaY,
                cropRect.getRight() + clampedDeltaX,
                cropRect.getBottom() + clampedDeltaY
        );
    }

    private static void validateMinimumSize(float minimumWidth, float minimumHeight) {
        if (!Float.isFinite(minimumWidth)
                || !Float.isFinite(minimumHeight)
                || minimumWidth <= 0f
                || minimumHeight <= 0f
                || minimumWidth > 1f
                || minimumHeight > 1f) {
            throw new IllegalArgumentException("Minimum crop size must be in range 0..1");
        }
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public enum Handle {
        TOP_LEFT,
        TOP_CENTER,
        TOP_RIGHT,
        CENTER_RIGHT,
        BOTTOM_RIGHT,
        BOTTOM_CENTER,
        BOTTOM_LEFT,
        CENTER_LEFT,
        MOVE
    }
}
