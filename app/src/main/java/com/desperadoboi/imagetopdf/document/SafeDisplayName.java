package com.desperadoboi.imagetopdf.document;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class SafeDisplayName {
    static final int MAX_CODE_POINTS = 120;
    private static final String FALLBACK = "document";

    private SafeDisplayName() {
    }

    public static String sanitize(String candidate) {
        String safe = sanitizeOrNull(candidate);
        return safe == null ? FALLBACK : safe;
    }

    public static String resolve(
            String providerDisplayName,
            String storedDisplayName,
            String uriName,
            String localizedFallback
    ) {
        String[] candidates = {
                providerDisplayName,
                storedDisplayName,
                decodeUriName(uriName)
        };
        for (String candidate : candidates) {
            String safe = sanitizeOrNull(candidate);
            if (safe != null) return safe;
        }
        String fallback = sanitizeOrNull(localizedFallback);
        return fallback == null ? FALLBACK : fallback;
    }

    public static String sanitizeOrNull(String candidate) {
        if (candidate == null) return null;
        String normalized = candidate.trim();
        if (normalized.isEmpty() || isInternalIdentifier(normalized)) return null;
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
            return null;
        }
        if (isInternalIdentifier(normalized)) return null;
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

    private static String decodeUriName(String candidate) {
        if (candidate == null || candidate.indexOf('%') < 0) return candidate;
        try {
            return URLDecoder.decode(
                    candidate.replace("+", "%2B"),
                    StandardCharsets.UTF_8.name()
            );
        } catch (IllegalArgumentException exception) {
            return null;
        } catch (java.io.UnsupportedEncodingException impossible) {
            return null;
        }
    }

    private static boolean isInternalIdentifier(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("content://") || lower.startsWith("file://")) return true;
        if (lower.matches("(?:msf|document):[a-z0-9._-]+")) return true;
        return lower.matches("viewer_[a-z0-9-]+\\.cache");
    }
}
