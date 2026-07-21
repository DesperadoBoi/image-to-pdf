package com.desperadoboi.imagetopdf.util;

import com.desperadoboi.imagetopdf.model.PdfResult;

import java.util.Locale;
import java.util.Objects;

public final class PdfResultSizeFormatter {
    private PdfResultSizeFormatter() {
    }

    public static String format(PdfResult result, Locale locale, String unknownLabel) {
        Objects.requireNonNull(result, "result is required");
        Objects.requireNonNull(locale, "locale is required");
        if (!result.hasKnownSize()) {
            if (unknownLabel == null || unknownLabel.trim().isEmpty()) {
                throw new IllegalArgumentException("unknownLabel is required");
            }
            return unknownLabel;
        }
        return FileSizeFormatter.format(result.getSizeBytes(), locale);
    }
}
