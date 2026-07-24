package com.desperadoboi.imagetopdf.document.word;

/**
 * Converts WordprocessingML measurements without passing them through Android dp/sp units.
 *
 * <p>Document geometry uses physical pixels. Text uses the same physical point conversion
 * and applies the Android font scale exactly once.</p>
 */
public final class WordMeasurementConverter {
    public static final float POINTS_PER_INCH = 72f;
    public static final float TWIPS_PER_POINT = 20f;
    public static final long EMU_PER_INCH = 914_400L;
    public static final float AUTO_LINE_UNITS_PER_LINE = 240f;
    public static final float PERCENT_UNITS_PER_PERCENT = 50f;

    private static final float MINIMUM_USABLE_XDPI = 72f;
    private static final float MAXIMUM_USABLE_XDPI = 1_000f;
    private static final int MINIMUM_FONT_HALF_POINTS = 2;
    private static final int MAXIMUM_FONT_HALF_POINTS = 800;
    private static final float MAXIMUM_TEXT_PIXELS = 4_096f;

    private final float pixelsPerInch;
    private final float fontScale;

    public WordMeasurementConverter(float xdpi, float density, float fontScale) {
        float logicalDpi = isPositiveFinite(density) ? density * 160f : 160f;
        pixelsPerInch = isPositiveFinite(xdpi)
                && xdpi >= MINIMUM_USABLE_XDPI
                && xdpi <= MAXIMUM_USABLE_XDPI
                ? xdpi
                : logicalDpi;
        this.fontScale = isPositiveFinite(fontScale)
                ? clamp(fontScale, 0.5f, 3f)
                : 1f;
    }

    public float getPixelsPerInch() {
        return pixelsPerInch;
    }

    public float getFontScale() {
        return fontScale;
    }

    public static float halfPointsToPoints(int halfPoints) {
        return halfPoints / 2f;
    }

    public static Float safeFontPointsFromHalfPoints(int halfPoints) {
        if (halfPoints < MINIMUM_FONT_HALF_POINTS
                || halfPoints > MAXIMUM_FONT_HALF_POINTS) {
            return null;
        }
        return halfPointsToPoints(halfPoints);
    }

    public static float twipsToPoints(float twips) {
        return twips / TWIPS_PER_POINT;
    }

    public float pointsToPhysicalPixels(float points) {
        if (!Float.isFinite(points)) return 0f;
        return points * pixelsPerInch / POINTS_PER_INCH;
    }

    public float fontPointsToPixels(float points) {
        float pixels = pointsToPhysicalPixels(points) * fontScale;
        return clamp(pixels, -MAXIMUM_TEXT_PIXELS, MAXIMUM_TEXT_PIXELS);
    }

    public float twipsToPixels(float twips) {
        return pointsToPhysicalPixels(twipsToPoints(twips));
    }

    public float emuToPixels(long emu) {
        if (emu <= 0L) return 0f;
        double pixels = emu * (double) pixelsPerInch / EMU_PER_INCH;
        return Double.isFinite(pixels) && pixels < Float.MAX_VALUE
                ? (float) pixels
                : Float.MAX_VALUE;
    }

    public float eighthPointsToPixels(int eighthPoints) {
        return pointsToPhysicalPixels(Math.max(0, eighthPoints) / 8f);
    }

    public static float percentageToFraction(int fiftiethsOfPercent) {
        return fiftiethsOfPercent / (PERCENT_UNITS_PER_PERCENT * 100f);
    }

    public static float percentageOf(int fiftiethsOfPercent, float availablePixels) {
        if (!Float.isFinite(availablePixels) || availablePixels <= 0f) return 0f;
        return Math.max(0f, availablePixels * percentageToFraction(
                Math.max(0, fiftiethsOfPercent)
        ));
    }

    public float tableWidthToPixels(
            WordTableWidth width,
            float availablePixels
    ) {
        if (width == null) return 0f;
        if (width.getType() == WordTableWidth.Type.DXA) {
            return Math.max(0f, twipsToPixels(width.getValue()));
        }
        if (width.getType() == WordTableWidth.Type.PERCENT) {
            return percentageOf(width.getValue(), availablePixels);
        }
        return 0f;
    }

    public static float autoLineSpacingMultiplier(int lineValue) {
        if (lineValue <= 0) return 1f;
        return clamp(lineValue / AUTO_LINE_UNITS_PER_LINE, 0.5f, 10f);
    }

    public float characterUnitsToPixels(
            int hundredthsOfCharacter,
            float fontSizePixels
    ) {
        if (hundredthsOfCharacter == 0 || !Float.isFinite(fontSizePixels)) return 0f;
        return hundredthsOfCharacter / 100f * fontSizePixels * 0.5f;
    }

    private static boolean isPositiveFinite(float value) {
        return Float.isFinite(value) && value > 0f;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
