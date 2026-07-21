package com.desperadoboi.imagetopdf.model;

import java.util.Locale;

public final class PdfFileNameFormatter {
    public static final String PDF_EXTENSION = ".pdf";
    public static final int MAX_BASE_NAME_LENGTH = 96;

    private static final String WORD_JOINER = "\u2060";

    private PdfFileNameFormatter() {
    }

    public static String normalizeFileName(String value) {
        String baseName = sanitizeBaseName(value);
        return shortenBaseName(baseName) + PDF_EXTENSION;
    }

    public static String toEditableName(String value) {
        if (value == null) {
            return "";
        }
        return stripPdfExtensions(value.trim());
    }

    public static String toDisplayTitle(String value) {
        String normalized = normalizeFileName(value);
        String baseName = normalized.substring(
                0,
                normalized.length() - PDF_EXTENSION.length()
        );
        return baseName + WORD_JOINER + PDF_EXTENSION;
    }

    private static String sanitizeBaseName(String value) {
        if (value == null) {
            throw new IllegalArgumentException("fileName is required");
        }
        String normalized = stripPdfExtensions(value.trim());
        normalized = normalized.replaceAll("[\\p{Cntrl}\\\\/:*?\"<>|]", "_");
        normalized = normalized.replaceAll("^[.\\s]+|[.\\s]+$", "");
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("fileName must not be empty");
        }
        return normalized;
    }

    private static String stripPdfExtensions(String value) {
        String result = value;
        while (result.toLowerCase(Locale.ROOT).endsWith(PDF_EXTENSION)) {
            result = result.substring(0, result.length() - PDF_EXTENSION.length()).trim();
        }
        return result;
    }

    private static String shortenBaseName(String value) {
        if (value.length() <= MAX_BASE_NAME_LENGTH) {
            return value;
        }
        int suffixLength = (MAX_BASE_NAME_LENGTH - 1) / 3;
        int prefixLength = MAX_BASE_NAME_LENGTH - suffixLength - 1;
        return value.substring(0, prefixLength)
                + "\u2026"
                + value.substring(value.length() - suffixLength);
    }
}
