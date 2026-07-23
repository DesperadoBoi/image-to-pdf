package com.desperadoboi.imagetopdf.document.word;

public final class WordParagraphStyle {
    public enum Alignment {
        LEFT,
        CENTER,
        RIGHT,
        JUSTIFY
    }

    public enum LineRule {
        AUTO,
        EXACT,
        AT_LEAST
    }

    private final String styleId;
    private final Alignment alignment;
    private final Integer leftIndentTwips;
    private final Integer rightIndentTwips;
    private final Integer firstLineIndentTwips;
    private final Integer hangingIndentTwips;
    private final Integer startIndentCharacters;
    private final Integer endIndentCharacters;
    private final Integer spaceBeforeTwips;
    private final Integer spaceAfterTwips;
    private final Integer lineSpacingValue;
    private final LineRule lineRule;
    private final Boolean contextualSpacing;
    private final Boolean beforeAutoSpacing;
    private final Boolean afterAutoSpacing;
    private final Boolean bidirectional;
    private final Boolean keepTogether;
    private final Boolean pageBreakBefore;
    private final Integer outlineLevel;
    private final Integer headingLevel;

    private WordParagraphStyle(Builder builder) {
        styleId = builder.styleId;
        alignment = builder.alignment;
        leftIndentTwips = builder.leftIndentTwips;
        rightIndentTwips = builder.rightIndentTwips;
        firstLineIndentTwips = builder.firstLineIndentTwips;
        hangingIndentTwips = builder.hangingIndentTwips;
        startIndentCharacters = builder.startIndentCharacters;
        endIndentCharacters = builder.endIndentCharacters;
        spaceBeforeTwips = builder.spaceBeforeTwips;
        spaceAfterTwips = builder.spaceAfterTwips;
        lineSpacingValue = builder.lineSpacingValue;
        lineRule = builder.lineRule;
        contextualSpacing = builder.contextualSpacing;
        beforeAutoSpacing = builder.beforeAutoSpacing;
        afterAutoSpacing = builder.afterAutoSpacing;
        bidirectional = builder.bidirectional;
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
        Integer resolvedFirstLine = safeBase.firstLineIndentTwips;
        Integer resolvedHanging = safeBase.hangingIndentTwips;
        if (overlay.firstLineIndentTwips != null) {
            resolvedFirstLine = overlay.firstLineIndentTwips;
            resolvedHanging = null;
        } else if (overlay.hangingIndentTwips != null) {
            resolvedFirstLine = null;
            resolvedHanging = overlay.hangingIndentTwips;
        }
        return new Builder()
                .setStyleId(first(overlay.styleId, safeBase.styleId))
                .setAlignment(first(overlay.alignment, safeBase.alignment))
                .setLeftIndentTwips(first(
                        overlay.leftIndentTwips,
                        safeBase.leftIndentTwips
                ))
                .setRightIndentTwips(first(
                        overlay.rightIndentTwips,
                        safeBase.rightIndentTwips
                ))
                .setFirstLineIndentTwips(resolvedFirstLine)
                .setHangingIndentTwips(resolvedHanging)
                .setStartIndentCharacters(first(
                        overlay.startIndentCharacters,
                        safeBase.startIndentCharacters
                ))
                .setEndIndentCharacters(first(
                        overlay.endIndentCharacters,
                        safeBase.endIndentCharacters
                ))
                .setSpaceBeforeTwips(first(
                        overlay.spaceBeforeTwips,
                        safeBase.spaceBeforeTwips
                ))
                .setSpaceAfterTwips(first(
                        overlay.spaceAfterTwips,
                        safeBase.spaceAfterTwips
                ))
                .setLineSpacingValue(first(
                        overlay.lineSpacingValue,
                        safeBase.lineSpacingValue
                ))
                .setLineRule(first(overlay.lineRule, safeBase.lineRule))
                .setContextualSpacing(first(
                        overlay.contextualSpacing,
                        safeBase.contextualSpacing
                ))
                .setBeforeAutoSpacing(first(
                        overlay.beforeAutoSpacing,
                        safeBase.beforeAutoSpacing
                ))
                .setAfterAutoSpacing(first(
                        overlay.afterAutoSpacing,
                        safeBase.afterAutoSpacing
                ))
                .setBidirectional(first(
                        overlay.bidirectional,
                        safeBase.bidirectional
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

    public String getStyleId() { return styleId == null ? "" : styleId; }
    public Alignment getAlignment() {
        return alignment == null ? Alignment.LEFT : alignment;
    }
    public int getLeftIndentTwips() { return value(leftIndentTwips); }
    public int getRightIndentTwips() { return value(rightIndentTwips); }
    public boolean hasLeftIndent() { return leftIndentTwips != null; }
    public boolean hasRightIndent() { return rightIndentTwips != null; }
    public int getFirstLineIndentTwips() { return value(firstLineIndentTwips); }
    public int getHangingIndentTwips() { return value(hangingIndentTwips); }
    public boolean hasFirstLineIndent() { return firstLineIndentTwips != null; }
    public boolean hasHangingIndent() { return hangingIndentTwips != null; }
    public int getStartIndentCharacters() { return value(startIndentCharacters); }
    public int getEndIndentCharacters() { return value(endIndentCharacters); }
    public int getSpaceBeforeTwips() { return value(spaceBeforeTwips); }
    public int getSpaceAfterTwips() { return value(spaceAfterTwips); }
    public int getLineSpacingValue() { return value(lineSpacingValue); }
    public LineRule getLineRule() { return lineRule == null ? LineRule.AUTO : lineRule; }
    public boolean isContextualSpacing() {
        return Boolean.TRUE.equals(contextualSpacing);
    }
    public boolean isBeforeAutoSpacing() {
        return Boolean.TRUE.equals(beforeAutoSpacing);
    }
    public boolean isAfterAutoSpacing() {
        return Boolean.TRUE.equals(afterAutoSpacing);
    }
    public boolean isBidirectional() { return Boolean.TRUE.equals(bidirectional); }
    public boolean isKeepTogether() { return Boolean.TRUE.equals(keepTogether); }
    public boolean isPageBreakBefore() { return Boolean.TRUE.equals(pageBreakBefore); }
    public int getOutlineLevel() { return value(outlineLevel, -1); }
    public int getHeadingLevel() { return value(headingLevel, 0); }

    private int value(Integer candidate) { return value(candidate, 0); }
    private int value(Integer candidate, int fallback) {
        return candidate == null ? fallback : candidate;
    }

    public static final class Builder {
        private String styleId;
        private Alignment alignment;
        private Integer leftIndentTwips;
        private Integer rightIndentTwips;
        private Integer firstLineIndentTwips;
        private Integer hangingIndentTwips;
        private Integer startIndentCharacters;
        private Integer endIndentCharacters;
        private Integer spaceBeforeTwips;
        private Integer spaceAfterTwips;
        private Integer lineSpacingValue;
        private LineRule lineRule;
        private Boolean contextualSpacing;
        private Boolean beforeAutoSpacing;
        private Boolean afterAutoSpacing;
        private Boolean bidirectional;
        private Boolean keepTogether;
        private Boolean pageBreakBefore;
        private Integer outlineLevel;
        private Integer headingLevel;

        public Builder setStyleId(String value) { styleId = value; return this; }
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
        public Builder setStartIndentCharacters(Integer value) {
            startIndentCharacters = value;
            return this;
        }
        public Builder setEndIndentCharacters(Integer value) {
            endIndentCharacters = value;
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
        public Builder setLineSpacingValue(Integer value) {
            lineSpacingValue = value;
            return this;
        }
        public Builder setLineRule(LineRule value) { lineRule = value; return this; }
        public Builder setContextualSpacing(Boolean value) {
            contextualSpacing = value;
            return this;
        }
        public Builder setBeforeAutoSpacing(Boolean value) {
            beforeAutoSpacing = value;
            return this;
        }
        public Builder setAfterAutoSpacing(Boolean value) {
            afterAutoSpacing = value;
            return this;
        }
        public Builder setBidirectional(Boolean value) {
            bidirectional = value;
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
