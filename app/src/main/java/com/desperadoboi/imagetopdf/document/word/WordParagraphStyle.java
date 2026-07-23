package com.desperadoboi.imagetopdf.document.word;

public final class WordParagraphStyle {
    public enum Alignment {
        LEFT,
        CENTER,
        RIGHT,
        JUSTIFY
    }

    private final Alignment alignment;
    private final Integer leftIndentTwips;
    private final Integer rightIndentTwips;
    private final Integer firstLineIndentTwips;
    private final Integer hangingIndentTwips;
    private final Integer spaceBeforeTwips;
    private final Integer spaceAfterTwips;
    private final Integer lineSpacingTwips;
    private final Boolean keepTogether;
    private final Boolean pageBreakBefore;
    private final Integer outlineLevel;
    private final Integer headingLevel;

    private WordParagraphStyle(Builder builder) {
        alignment = builder.alignment;
        leftIndentTwips = builder.leftIndentTwips;
        rightIndentTwips = builder.rightIndentTwips;
        firstLineIndentTwips = builder.firstLineIndentTwips;
        hangingIndentTwips = builder.hangingIndentTwips;
        spaceBeforeTwips = builder.spaceBeforeTwips;
        spaceAfterTwips = builder.spaceAfterTwips;
        lineSpacingTwips = builder.lineSpacingTwips;
        keepTogether = builder.keepTogether;
        pageBreakBefore = builder.pageBreakBefore;
        outlineLevel = builder.outlineLevel;
        headingLevel = builder.headingLevel;
    }

    public static WordParagraphStyle defaults() {
        return new Builder().setAlignment(Alignment.LEFT).build();
    }

    public static WordParagraphStyle merge(
            WordParagraphStyle base,
            WordParagraphStyle overlay
    ) {
        WordParagraphStyle safeBase = base == null ? defaults() : base;
        if (overlay == null) return safeBase;
        return new Builder()
                .setAlignment(first(overlay.alignment, safeBase.alignment))
                .setLeftIndentTwips(first(
                        overlay.leftIndentTwips,
                        safeBase.leftIndentTwips
                ))
                .setRightIndentTwips(first(
                        overlay.rightIndentTwips,
                        safeBase.rightIndentTwips
                ))
                .setFirstLineIndentTwips(first(
                        overlay.firstLineIndentTwips,
                        safeBase.firstLineIndentTwips
                ))
                .setHangingIndentTwips(first(
                        overlay.hangingIndentTwips,
                        safeBase.hangingIndentTwips
                ))
                .setSpaceBeforeTwips(first(
                        overlay.spaceBeforeTwips,
                        safeBase.spaceBeforeTwips
                ))
                .setSpaceAfterTwips(first(
                        overlay.spaceAfterTwips,
                        safeBase.spaceAfterTwips
                ))
                .setLineSpacingTwips(first(
                        overlay.lineSpacingTwips,
                        safeBase.lineSpacingTwips
                ))
                .setKeepTogether(first(
                        overlay.keepTogether,
                        safeBase.keepTogether
                ))
                .setPageBreakBefore(first(
                        overlay.pageBreakBefore,
                        safeBase.pageBreakBefore
                ))
                .setOutlineLevel(first(overlay.outlineLevel, safeBase.outlineLevel))
                .setHeadingLevel(first(overlay.headingLevel, safeBase.headingLevel))
                .build();
    }

    private static <T> T first(T preferred, T fallback) {
        return preferred == null ? fallback : preferred;
    }

    public Alignment getAlignment() {
        return alignment == null ? Alignment.LEFT : alignment;
    }
    public int getLeftIndentTwips() { return value(leftIndentTwips); }
    public int getRightIndentTwips() { return value(rightIndentTwips); }
    public int getFirstLineIndentTwips() { return value(firstLineIndentTwips); }
    public int getHangingIndentTwips() { return value(hangingIndentTwips); }
    public int getSpaceBeforeTwips() { return value(spaceBeforeTwips); }
    public int getSpaceAfterTwips() { return value(spaceAfterTwips); }
    public int getLineSpacingTwips() { return value(lineSpacingTwips); }
    public boolean isKeepTogether() { return Boolean.TRUE.equals(keepTogether); }
    public boolean isPageBreakBefore() { return Boolean.TRUE.equals(pageBreakBefore); }
    public int getOutlineLevel() { return value(outlineLevel, -1); }
    public int getHeadingLevel() { return value(headingLevel, 0); }

    private int value(Integer candidate) { return value(candidate, 0); }
    private int value(Integer candidate, int fallback) {
        return candidate == null ? fallback : candidate;
    }

    public static final class Builder {
        private Alignment alignment;
        private Integer leftIndentTwips;
        private Integer rightIndentTwips;
        private Integer firstLineIndentTwips;
        private Integer hangingIndentTwips;
        private Integer spaceBeforeTwips;
        private Integer spaceAfterTwips;
        private Integer lineSpacingTwips;
        private Boolean keepTogether;
        private Boolean pageBreakBefore;
        private Integer outlineLevel;
        private Integer headingLevel;

        public Builder setAlignment(Alignment value) { alignment = value; return this; }
        public Builder setLeftIndentTwips(Integer value) {
            leftIndentTwips = value;
            return this;
        }
        public Builder setRightIndentTwips(Integer value) {
            rightIndentTwips = value;
            return this;
        }
        public Builder setFirstLineIndentTwips(Integer value) {
            firstLineIndentTwips = value;
            return this;
        }
        public Builder setHangingIndentTwips(Integer value) {
            hangingIndentTwips = value;
            return this;
        }
        public Builder setSpaceBeforeTwips(Integer value) {
            spaceBeforeTwips = value;
            return this;
        }
        public Builder setSpaceAfterTwips(Integer value) {
            spaceAfterTwips = value;
            return this;
        }
        public Builder setLineSpacingTwips(Integer value) {
            lineSpacingTwips = value;
            return this;
        }
        public Builder setKeepTogether(Boolean value) {
            keepTogether = value;
            return this;
        }
        public Builder setPageBreakBefore(Boolean value) {
            pageBreakBefore = value;
            return this;
        }
        public Builder setOutlineLevel(Integer value) {
            outlineLevel = value;
            return this;
        }
        public Builder setHeadingLevel(Integer value) {
            headingLevel = value;
            return this;
        }
        public WordParagraphStyle build() { return new WordParagraphStyle(this); }
    }
}
