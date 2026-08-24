package com.desperadoboi.imagetopdf.ui.watermark;

import java.util.Locale;

public final class PdfWatermarkFileName {
    private static final int MAX_STEM_CODE_POINTS = 72;

    private PdfWatermarkFileName() {
    }

    public static String suggest(String sourceDisplayName, String isoDate) {
        String fallback = "Watermark_" + safeDate(isoDate) + ".pdf";
        if (sourceDisplayName == null) return fallback;
        String value = sourceDisplayName.trim();
        if (value.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            value = value.substring(0, value.length() - 4);
        }
        StringBuilder safe = new StringBuilder();
        boolean lastWasSeparator = false;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            boolean accepted = Character.isLetterOrDigit(codePoint)
                    || codePoint == '.' || codePoint == '-' || codePoint == '_';
            if (accepted) {
                safe.appendCodePoint(codePoint);
                lastWasSeparator = false;
            } else if (Character.isWhitespace(codePoint) && !lastWasSeparator
                    && safe.length() > 0) {
                safe.append('_');
                lastWasSeparator = true;
            }
            if (safe.codePointCount(0, safe.length()) >= MAX_STEM_CODE_POINTS) break;
        }
        String stem = safe.toString().replaceAll("[._-]+$", "");
        return stem.isEmpty() ? fallback : stem + "_watermark.pdf";
    }

    private static String safeDate(String value) {
        return value != null && value.matches("\\d{4}-\\d{2}-\\d{2}")
                ? value
                : "document";
    }
}
