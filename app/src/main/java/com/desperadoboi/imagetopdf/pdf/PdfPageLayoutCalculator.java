package com.desperadoboi.imagetopdf.pdf;

import com.desperadoboi.imagetopdf.model.PageSizeMode;
import com.desperadoboi.imagetopdf.model.PdfOptions;

import java.util.Objects;

public final class PdfPageLayoutCalculator {
    public static final int A4_WIDTH_POINTS = 595;
    public static final int A4_HEIGHT_POINTS = 842;
    public static final int MAX_PAGE_LONG_SIDE_POINTS = 842;

    private PdfPageLayoutCalculator() {
    }

    public static PdfPageLayout calculate(
            PdfOptions pdfOptions,
            int orientedImageWidth,
            int orientedImageHeight
    ) {
        Objects.requireNonNull(pdfOptions, "pdfOptions is required");
        if (orientedImageWidth <= 0 || orientedImageHeight <= 0) {
            throw new IllegalArgumentException("Image dimensions must be positive");
        }

        int margin = pdfOptions.getMarginPreset().getMarginPoints();
        if (pdfOptions.getPageSizeMode() == PageSizeMode.A4) {
            return calculateA4(margin);
        }
        return calculateImageSized(orientedImageWidth, orientedImageHeight, margin);
    }

    private static PdfPageLayout calculateA4(int margin) {
        return new PdfPageLayout(
                A4_WIDTH_POINTS,
                A4_HEIGHT_POINTS,
                margin,
                margin,
                A4_WIDTH_POINTS - margin,
                A4_HEIGHT_POINTS - margin
        );
    }

    private static PdfPageLayout calculateImageSized(int imageWidth, int imageHeight, int margin) {
        int contentLongSide = MAX_PAGE_LONG_SIDE_POINTS - (margin * 2);
        if (contentLongSide <= 0) {
            throw new IllegalArgumentException("Content area must be positive");
        }

        float contentWidth;
        float contentHeight;
        if (imageWidth >= imageHeight) {
            contentWidth = contentLongSide;
            contentHeight = contentLongSide * (imageHeight / (float) imageWidth);
        } else {
            contentHeight = contentLongSide;
            contentWidth = contentLongSide * (imageWidth / (float) imageHeight);
        }

        int roundedContentWidth = Math.max(1, Math.round(contentWidth));
        int roundedContentHeight = Math.max(1, Math.round(contentHeight));
        int pageWidth = roundedContentWidth + (margin * 2);
        int pageHeight = roundedContentHeight + (margin * 2);

        return new PdfPageLayout(
                pageWidth,
                pageHeight,
                margin,
                margin,
                margin + roundedContentWidth,
                margin + roundedContentHeight
        );
    }
}
