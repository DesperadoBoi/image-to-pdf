package com.desperadoboi.imagetopdf.pdf;

public final class PdfPageLayout {
    private final int pageWidth;
    private final int pageHeight;
    private final float contentLeft;
    private final float contentTop;
    private final float contentRight;
    private final float contentBottom;

    public PdfPageLayout(
            int pageWidth,
            int pageHeight,
            float contentLeft,
            float contentTop,
            float contentRight,
            float contentBottom
    ) {
        if (pageWidth <= 0 || pageHeight <= 0) {
            throw new IllegalArgumentException("Page dimensions must be positive");
        }
        if (contentLeft < 0f || contentTop < 0f || contentRight < 0f || contentBottom < 0f) {
            throw new IllegalArgumentException("Content bounds must not be negative");
        }
        if (contentRight <= contentLeft || contentBottom <= contentTop) {
            throw new IllegalArgumentException("Content area must be positive");
        }
        if (contentRight > pageWidth || contentBottom > pageHeight) {
            throw new IllegalArgumentException("Content bounds must be inside page");
        }

        this.pageWidth = pageWidth;
        this.pageHeight = pageHeight;
        this.contentLeft = contentLeft;
        this.contentTop = contentTop;
        this.contentRight = contentRight;
        this.contentBottom = contentBottom;
    }

    public int getPageWidth() {
        return pageWidth;
    }

    public int getPageHeight() {
        return pageHeight;
    }

    public float getContentLeft() {
        return contentLeft;
    }

    public float getContentTop() {
        return contentTop;
    }

    public float getContentRight() {
        return contentRight;
    }

    public float getContentBottom() {
        return contentBottom;
    }

    public float getContentWidth() {
        return contentRight - contentLeft;
    }

    public float getContentHeight() {
        return contentBottom - contentTop;
    }
}
