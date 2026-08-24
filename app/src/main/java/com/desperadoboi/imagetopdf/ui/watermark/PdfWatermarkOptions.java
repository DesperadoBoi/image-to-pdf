package com.desperadoboi.imagetopdf.ui.watermark;

import java.util.Objects;

public final class PdfWatermarkOptions {
    public static final int MAX_TEXT_LENGTH = 60;
    public static final float MIN_OPACITY = 0.10f;
    public static final float MAX_OPACITY = 0.80f;
    public static final float DEFAULT_OPACITY = 0.30f;
    public static final float MIN_SIZE_POINTS = 18f;
    public static final float MAX_SIZE_POINTS = 96f;
    public static final float DEFAULT_SIZE_POINTS = 48f;
    public static final float MIN_ROTATION_DEGREES = -60f;
    public static final float MAX_ROTATION_DEGREES = 60f;
    public static final float DEFAULT_ROTATION_DEGREES = -45f;

    private final String text;
    private final WatermarkStyle style;
    private final WatermarkPosition position;
    private final float opacity;
    private final float sizePoints;
    private final float rotationDegrees;
    private final WatermarkColor color;

    public PdfWatermarkOptions(
            String text,
            WatermarkStyle style,
            WatermarkPosition position,
            float opacity,
            float sizePoints,
            float rotationDegrees,
            WatermarkColor color
    ) {
        String safeText = text == null ? "" : text;
        if (safeText.codePointCount(0, safeText.length()) > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("watermark text is too long");
        }
        requireRange(opacity, MIN_OPACITY, MAX_OPACITY, "opacity");
        requireRange(sizePoints, MIN_SIZE_POINTS, MAX_SIZE_POINTS, "sizePoints");
        requireRange(
                rotationDegrees,
                MIN_ROTATION_DEGREES,
                MAX_ROTATION_DEGREES,
                "rotationDegrees"
        );
        this.text = safeText;
        this.style = Objects.requireNonNull(style, "style is required");
        this.position = Objects.requireNonNull(position, "position is required");
        this.opacity = opacity;
        this.sizePoints = sizePoints;
        this.rotationDegrees = rotationDegrees;
        this.color = Objects.requireNonNull(color, "color is required");
    }

    public static PdfWatermarkOptions defaults(String localizedText) {
        return new PdfWatermarkOptions(
                localizedText,
                WatermarkStyle.SINGLE,
                WatermarkPosition.CENTER,
                DEFAULT_OPACITY,
                DEFAULT_SIZE_POINTS,
                DEFAULT_ROTATION_DEGREES,
                WatermarkColor.GRAY
        );
    }

    public String getText() { return text; }
    public WatermarkStyle getStyle() { return style; }
    public WatermarkPosition getPosition() { return position; }
    public float getOpacity() { return opacity; }
    public float getSizePoints() { return sizePoints; }
    public float getRotationDegrees() { return rotationDegrees; }
    public WatermarkColor getColor() { return color; }

    public boolean hasValidText() {
        return !text.trim().isEmpty();
    }

    public PdfWatermarkOptions withText(String value) {
        return new PdfWatermarkOptions(
                value,
                style,
                position,
                opacity,
                sizePoints,
                rotationDegrees,
                color
        );
    }

    public PdfWatermarkOptions withStyle(WatermarkStyle value) {
        return new PdfWatermarkOptions(
                text,
                value,
                position,
                opacity,
                sizePoints,
                rotationDegrees,
                color
        );
    }

    public PdfWatermarkOptions withPosition(WatermarkPosition value) {
        return new PdfWatermarkOptions(
                text,
                style,
                value,
                opacity,
                sizePoints,
                rotationDegrees,
                color
        );
    }

    public PdfWatermarkOptions withOpacity(float value) {
        return new PdfWatermarkOptions(
                text,
                style,
                position,
                value,
                sizePoints,
                rotationDegrees,
                color
        );
    }

    public PdfWatermarkOptions withSizePoints(float value) {
        return new PdfWatermarkOptions(
                text,
                style,
                position,
                opacity,
                value,
                rotationDegrees,
                color
        );
    }

    public PdfWatermarkOptions withRotationDegrees(float value) {
        return new PdfWatermarkOptions(
                text,
                style,
                position,
                opacity,
                sizePoints,
                value,
                color
        );
    }

    public PdfWatermarkOptions withColor(WatermarkColor value) {
        return new PdfWatermarkOptions(
                text,
                style,
                position,
                opacity,
                sizePoints,
                rotationDegrees,
                value
        );
    }

    private static void requireRange(float value, float min, float max, String name) {
        if (!Float.isFinite(value) || value < min || value > max) {
            throw new IllegalArgumentException(name + " must be within " + min + ".." + max);
        }
    }
}
