package com.desperadoboi.imagetopdf.ui.about;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

public final class AboutPrivacyFormatter {
    private AboutPrivacyFormatter() {
    }

    public static String resolveVersionName(String versionName, String fallback) {
        String safeFallback = Objects.requireNonNull(fallback, "fallback is required");
        if (versionName == null || versionName.trim().isEmpty()) {
            return safeFallback;
        }
        return versionName.trim();
    }

    public static String createMailtoUri(String email, String subject) {
        String safeEmail = requireNonBlank(email, "email");
        String safeSubject = requireNonBlank(subject, "subject");
        try {
            return new URI(
                    "mailto",
                    safeEmail + "?subject=" + safeSubject,
                    null
            ).toASCIIString();
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Invalid email or subject", exception);
        }
    }

    public static String requireHttpsUri(String url) {
        String safeUrl = requireNonBlank(url, "url");
        try {
            URI uri = new URI(safeUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getUserInfo() != null) {
                throw new IllegalArgumentException("A public HTTPS URL is required");
            }
            return uri.toASCIIString();
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Invalid public HTTPS URL", exception);
        }
    }

    private static String requireNonBlank(String value, String label) {
        Objects.requireNonNull(value, label + " is required");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return trimmed;
    }
}
