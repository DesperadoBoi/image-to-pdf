package com.desperadoboi.imagetopdf.image;

import java.util.UUID;

public final class CapturedImageFileNameGenerator {
    private static final String PREFIX = "capture_";
    private static final String EXTENSION = ".jpg";
    private static final String SAFE_TOKEN_PATTERN = "[A-Za-z0-9_-]+";

    public String createFileName() {
        return buildFileName(UUID.randomUUID().toString());
    }

    public static String buildFileName(String token) {
        if (token == null) {
            throw new IllegalArgumentException("token is required");
        }
        String normalizedToken = token.trim();
        if (normalizedToken.isEmpty() || !normalizedToken.matches(SAFE_TOKEN_PATTERN)) {
            throw new IllegalArgumentException("token contains unsupported characters");
        }
        return PREFIX + normalizedToken + EXTENSION;
    }

    public static boolean isGeneratedFileName(String fileName) {
        if (fileName == null) {
            return false;
        }
        String value = fileName.trim();
        return value.startsWith(PREFIX)
                && value.endsWith(EXTENSION)
                && value.length() > PREFIX.length() + EXTENSION.length()
                && value.substring(PREFIX.length(), value.length() - EXTENSION.length())
                .matches(SAFE_TOKEN_PATTERN);
    }
}
