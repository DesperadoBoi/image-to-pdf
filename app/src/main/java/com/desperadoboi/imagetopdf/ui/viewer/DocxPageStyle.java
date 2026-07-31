package com.desperadoboi.imagetopdf.ui.viewer;

import androidx.annotation.Nullable;

import com.desperadoboi.imagetopdf.document.word.WordSectionProperties;

import java.util.Locale;

final class DocxPageStyle {
    static final double A4_WIDTH_POINTS = 595.3d;
    static final double A4_HEIGHT_POINTS = 841.9d;
    private static final double DEFAULT_MARGIN_POINTS = 72d;
    private static final double MIN_PAGE_POINTS = 144d;
    private static final double MAX_PAGE_POINTS = 2_000d;
    private static final double MIN_CONTENT_POINTS = 72d;

    private final double widthPoints;
    private final double heightPoints;
    private final double marginTopPoints;
    private final double marginRightPoints;
    private final double marginBottomPoints;
    private final double marginLeftPoints;

    private DocxPageStyle(
            double widthPoints,
            double heightPoints,
            double marginTopPoints,
            double marginRightPoints,
            double marginBottomPoints,
            double marginLeftPoints
    ) {
        this.widthPoints = widthPoints;
        this.heightPoints = heightPoints;
        this.marginTopPoints = marginTopPoints;
        this.marginRightPoints = marginRightPoints;
        this.marginBottomPoints = marginBottomPoints;
        this.marginLeftPoints = marginLeftPoints;
    }

    static DocxPageStyle from(@Nullable WordSectionProperties section) {
        double width = section == null || section.getPageWidthTwips() <= 0
                ? A4_WIDTH_POINTS
                : twips(section.getPageWidthTwips());
        double height = section == null || section.getPageHeightTwips() <= 0
                ? A4_HEIGHT_POINTS
                : twips(section.getPageHeightTwips());
        width = clamp(width, MIN_PAGE_POINTS, MAX_PAGE_POINTS);
        height = clamp(height, MIN_PAGE_POINTS, MAX_PAGE_POINTS);

        double top = margin(section == null ? 0 : section.getMarginTopTwips());
        double right = margin(section == null ? 0 : section.getMarginRightTwips());
        double bottom = margin(section == null ? 0 : section.getMarginBottomTwips());
        double left = margin(section == null ? 0 : section.getMarginLeftTwips());
        double[] horizontal = fitMargins(left, right, width);
        double[] vertical = fitMargins(top, bottom, height);
        return new DocxPageStyle(
                width,
                height,
                vertical[0],
                horizontal[1],
                vertical[1],
                horizontal[0]
        );
    }

    private static double margin(int twips) {
        return twips <= 0 ? DEFAULT_MARGIN_POINTS : twips(twips);
    }

    private static double[] fitMargins(double leading, double trailing, double pageSize) {
        double safeLeading = clamp(leading, 0d, pageSize * 0.45d);
        double safeTrailing = clamp(trailing, 0d, pageSize * 0.45d);
        double maximumTotal = Math.max(0d, pageSize - MIN_CONTENT_POINTS);
        double total = safeLeading + safeTrailing;
        if (total > maximumTotal && total > 0d) {
            double scale = maximumTotal / total;
            safeLeading *= scale;
            safeTrailing *= scale;
        }
        return new double[]{safeLeading, safeTrailing};
    }

    String inlineCss() {
        return "width:" + points(widthPoints)
                + ";height:" + points(heightPoints)
                + ";padding:" + points(marginTopPoints)
                + " " + points(marginRightPoints)
                + " " + points(marginBottomPoints)
                + " " + points(marginLeftPoints);
    }

    String orientation() {
        return widthPoints > heightPoints ? "landscape" : "portrait";
    }

    double contentWidthPoints() {
        return Math.max(MIN_CONTENT_POINTS, widthPoints - marginLeftPoints - marginRightPoints);
    }

    double contentHeightPoints() {
        return Math.max(MIN_CONTENT_POINTS, heightPoints - marginTopPoints - marginBottomPoints);
    }

    double widthPoints() {
        return widthPoints;
    }

    double heightPoints() {
        return heightPoints;
    }

    static double twips(int value) {
        return value / 20d;
    }

    static String points(double value) {
        return String.format(Locale.US, "%.2fpt", value);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
