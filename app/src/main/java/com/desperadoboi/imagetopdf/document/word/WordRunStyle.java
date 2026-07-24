package com.desperadoboi.imagetopdf.document.word;

import java.util.Locale;

public final class WordRunStyle {
    private final String fontFamily;
    private final Float fontSizePoints;
    private final Boolean bold;
    private final Boolean italic;
    private final Boolean underline;
    private final Boolean strike;
    private final Integer color;
    private final Integer highlight;
    private final VerticalPosition verticalPosition;
    private final Float baselineShiftPoints;
    private final Boolean hidden;

    public enum VerticalPosition {
        BASELINE,
        SUBSCRIPT,
        SUPERSCRIPT
    }

    private WordRunStyle(Builder builder) {
        fontFamily = builder.fontFamily;
        fontSizePoints = builder.fontSizePoints;
        bold = builder.bold;
        italic = builder.italic;
        underline = builder.underline;
        strike = builder.strike;
        color = builder.color;
        highlight = builder.highlight;
        verticalPosition = builder.verticalPosition;
        baselineShiftPoints = builder.baselineShiftPoints;
        hidden = builder.hidden;
    }

    public static WordRunStyle defaults() {
        return new Builder()
                .setFontFamily("sans-serif")
                .setFontSizePoints(11f)
                .setBold(false)
                .setItalic(false)
                .setUnderline(false)
                .setStrike(false)
                .setColor(0xFF1D2633)
                .setVerticalPosition(VerticalPosition.BASELINE)
                .setHidden(false)
                .build();
    }

    public static WordRunStyle merge(WordRunStyle base, WordRunStyle overlay) {
        if (base == null) return overlay;
        if (overlay == null) return base;
        return new Builder()
                .setFontFamily(first(overlay.fontFamily, base.fontFamily))
                .setFontSizePoints(first(overlay.fontSizePoints, base.fontSizePoints))
                .setBold(first(overlay.bold, base.bold))
                .setItalic(first(overlay.italic, base.italic))
                .setUnderline(first(overlay.underline, base.underline))
                .setStrike(first(overlay.strike, base.strike))
                .setColor(first(overlay.color, base.color))
                .setHighlight(first(overlay.highlight, base.highlight))
                .setVerticalPosition(first(
                        overlay.verticalPosition,
                        base.verticalPosition
                ))
                .setBaselineShiftPoints(first(
                        overlay.baselineShiftPoints,
                        base.baselineShiftPoints
                ))
                .setHidden(first(overlay.hidden, base.hidden))
                .build();
    }

    private static <T> T first(T preferred, T fallback) {
        return preferred == null ? fallback : preferred;
    }

    public String getFontFamily() {
        return fontFamily == null ? "sans-serif" : safeAndroidFamily(fontFamily);
    }

    public float getFontSizePoints() {
        float value = fontSizePoints == null ? 11f : fontSizePoints;
        return Float.isFinite(value) ? Math.max(1f, Math.min(400f, value)) : 11f;
    }

    public boolean isBold() { return Boolean.TRUE.equals(bold); }
    public boolean isItalic() { return Boolean.TRUE.equals(italic); }
    public boolean isUnderline() { return Boolean.TRUE.equals(underline); }
    public boolean isStrike() { return Boolean.TRUE.equals(strike); }
    public Integer getColor() { return color; }
    public Integer getHighlight() { return highlight; }
    public VerticalPosition getVerticalPosition() {
        return verticalPosition == null ? VerticalPosition.BASELINE : verticalPosition;
    }
    public float getBaselineShiftPoints() {
        return baselineShiftPoints == null || !Float.isFinite(baselineShiftPoints)
                ? 0f
                : Math.max(-400f, Math.min(400f, baselineShiftPoints));
    }
    public boolean isHidden() { return Boolean.TRUE.equals(hidden); }

    private String safeAndroidFamily(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        if (normalized.contains("mono") || normalized.contains("courier")
                || normalized.contains("consolas")) {
            return "monospace";
        }
        if (normalized.contains("sans") || normalized.contains("calibri")
                || normalized.contains("arial") || normalized.contains("roboto")
                || normalized.contains("segoe")
                || normalized.contains("helvetica")) {
            return "sans-serif";
        }
        if (normalized.contains("serif") || normalized.contains("times")
                || normalized.contains("georgia") || normalized.contains("cambria")) {
            return "serif";
        }
        return "sans-serif";
    }

    public static final class Builder {
        private String fontFamily;
        private Float fontSizePoints;
        private Boolean bold;
        private Boolean italic;
        private Boolean underline;
        private Boolean strike;
        private Integer color;
        private Integer highlight;
        private VerticalPosition verticalPosition;
        private Float baselineShiftPoints;
        private Boolean hidden;

        public Builder setFontFamily(String value) { fontFamily = value; return this; }
        public Builder setFontSizePoints(Float value) { fontSizePoints = value; return this; }
        public Builder setBold(Boolean value) { bold = value; return this; }
        public Builder setItalic(Boolean value) { italic = value; return this; }
        public Builder setUnderline(Boolean value) { underline = value; return this; }
        public Builder setStrike(Boolean value) { strike = value; return this; }
        public Builder setColor(Integer value) { color = value; return this; }
        public Builder setHighlight(Integer value) { highlight = value; return this; }
        public Builder setVerticalPosition(VerticalPosition value) {
            verticalPosition = value;
            return this;
        }
        public Builder setBaselineShiftPoints(Float value) {
            baselineShiftPoints = value;
            return this;
        }
        public Builder setHidden(Boolean value) { hidden = value; return this; }
        public WordRunStyle build() { return new WordRunStyle(this); }
    }
}
