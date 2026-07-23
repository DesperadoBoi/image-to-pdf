package com.desperadoboi.imagetopdf.document.spreadsheet;

import androidx.annotation.Nullable;

public final class SpreadsheetCellStyle {
    public enum HorizontalAlignment {
        GENERAL,
        LEFT,
        CENTER,
        RIGHT,
        JUSTIFY
    }

    public enum VerticalAlignment {
        TOP,
        CENTER,
        BOTTOM
    }

    public static final SpreadsheetCellStyle DEFAULT = new Builder().build();

    @Nullable private final Integer fillColor;
    @Nullable private final Integer fontColor;
    private final boolean bold;
    private final boolean italic;
    private final boolean underline;
    private final float fontSizePoints;
    private final HorizontalAlignment horizontalAlignment;
    private final VerticalAlignment verticalAlignment;
    private final boolean wrapText;
    private final SpreadsheetBorder leftBorder;
    private final SpreadsheetBorder topBorder;
    private final SpreadsheetBorder rightBorder;
    private final SpreadsheetBorder bottomBorder;

    private SpreadsheetCellStyle(Builder builder) {
        fillColor = builder.fillColor;
        fontColor = builder.fontColor;
        bold = builder.bold;
        italic = builder.italic;
        underline = builder.underline;
        fontSizePoints = builder.fontSizePoints;
        horizontalAlignment = builder.horizontalAlignment;
        verticalAlignment = builder.verticalAlignment;
        wrapText = builder.wrapText;
        leftBorder = builder.leftBorder;
        topBorder = builder.topBorder;
        rightBorder = builder.rightBorder;
        bottomBorder = builder.bottomBorder;
    }

    @Nullable
    public Integer getFillColor() {
        return fillColor;
    }

    @Nullable
    public Integer getFontColor() {
        return fontColor;
    }

    public boolean isBold() {
        return bold;
    }

    public boolean isItalic() {
        return italic;
    }

    public boolean isUnderline() {
        return underline;
    }

    public float getFontSizePoints() {
        return fontSizePoints;
    }

    public HorizontalAlignment getHorizontalAlignment() {
        return horizontalAlignment;
    }

    public VerticalAlignment getVerticalAlignment() {
        return verticalAlignment;
    }

    public boolean isWrapText() {
        return wrapText;
    }

    public SpreadsheetBorder getLeftBorder() {
        return leftBorder;
    }

    public SpreadsheetBorder getTopBorder() {
        return topBorder;
    }

    public SpreadsheetBorder getRightBorder() {
        return rightBorder;
    }

    public SpreadsheetBorder getBottomBorder() {
        return bottomBorder;
    }

    public static final class Builder {
        @Nullable private Integer fillColor;
        @Nullable private Integer fontColor;
        private boolean bold;
        private boolean italic;
        private boolean underline;
        private float fontSizePoints;
        private HorizontalAlignment horizontalAlignment = HorizontalAlignment.GENERAL;
        private VerticalAlignment verticalAlignment = VerticalAlignment.BOTTOM;
        private boolean wrapText;
        private SpreadsheetBorder leftBorder = SpreadsheetBorder.NONE;
        private SpreadsheetBorder topBorder = SpreadsheetBorder.NONE;
        private SpreadsheetBorder rightBorder = SpreadsheetBorder.NONE;
        private SpreadsheetBorder bottomBorder = SpreadsheetBorder.NONE;

        public Builder setFillColor(@Nullable Integer fillColor) {
            this.fillColor = fillColor;
            return this;
        }

        public Builder setFontColor(@Nullable Integer fontColor) {
            this.fontColor = fontColor;
            return this;
        }

        public Builder setBold(boolean bold) {
            this.bold = bold;
            return this;
        }

        public Builder setItalic(boolean italic) {
            this.italic = italic;
            return this;
        }

        public Builder setUnderline(boolean underline) {
            this.underline = underline;
            return this;
        }

        public Builder setFontSizePoints(float fontSizePoints) {
            this.fontSizePoints = Math.max(0f, fontSizePoints);
            return this;
        }

        public Builder setHorizontalAlignment(HorizontalAlignment horizontalAlignment) {
            this.horizontalAlignment = horizontalAlignment;
            return this;
        }

        public Builder setVerticalAlignment(VerticalAlignment verticalAlignment) {
            this.verticalAlignment = verticalAlignment;
            return this;
        }

        public Builder setWrapText(boolean wrapText) {
            this.wrapText = wrapText;
            return this;
        }

        public Builder setBorders(
                SpreadsheetBorder left,
                SpreadsheetBorder top,
                SpreadsheetBorder right,
                SpreadsheetBorder bottom
        ) {
            leftBorder = left;
            topBorder = top;
            rightBorder = right;
            bottomBorder = bottom;
            return this;
        }

        public SpreadsheetCellStyle build() {
            return new SpreadsheetCellStyle(this);
        }
    }
}
