package com.desperadoboi.imagetopdf.document;

public final class SafeDisplayName {
    static final int MAX_CODE_POINTS = 120;
    private static final String FALLBACK = "document";

    private SafeDisplayName() {
    }

    public static String sanitize(String candidate) {
        if (candidate == null) {
            return FALLBACK;
        }
        String normalized = candidate.trim();
        int slash = Math.max(normalized.lastIndexOf('/'), normalized.lastIndexOf('\\'));
        if (slash >= 0) {
            normalized = normalized.substring(slash + 1).trim();
        }
        StringBuilder safe = new StringBuilder();
        normalized.codePoints().forEach(codePoint -> {
            if (!Character.isISOControl(codePoint)
                    && codePoint != '/'
                    && codePoint != '\\'
                    && codePoint != 0) {
                safe.appendCodePoint(codePoint);
            }
        });
        normalized = safe.toString().trim();
        if (normalized.isEmpty() || ".".equals(normalized) || "..".equals(normalized)) {
            return FALLBACK;
        }
        if (normalized.codePointCount(0, normalized.length()) <= MAX_CODE_POINTS) {
            return normalized;
        }
        int extensionStart = normalized.lastIndexOf('.');
        String extension = extensionStart > 0 && normalized.length() - extensionStart <= 16
                ? normalized.substring(extensionStart)
                : "";
        int suffixPoints = extension.codePointCount(0, extension.length());
        int prefixPoints = Math.max(1, MAX_CODE_POINTS - suffixPoints);
        int end = normalized.offsetByCodePoints(0, prefixPoints);
        return normalized.substring(0, end) + extension;
    }
}
