package com.desperadoboi.imagetopdf.util;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Objects;

public final class FileSizeFormatter {
    private static final long BYTES_PER_KIB = 1024L;
    private static final long BYTES_PER_MIB = BYTES_PER_KIB * 1024L;

    private FileSizeFormatter() {
    }

    public static String format(long sizeBytes, Locale locale) {
        Objects.requireNonNull(locale, "locale is required");
        if (sizeBytes < 0L) {
            throw new IllegalArgumentException("sizeBytes must be non-negative");
        }
        if (sizeBytes < BYTES_PER_KIB) {
            return sizeBytes + " B";
        }

        DecimalFormat decimalFormat = new DecimalFormat("0.#", DecimalFormatSymbols.getInstance(locale));
        decimalFormat.setRoundingMode(RoundingMode.DOWN);
        if (sizeBytes < BYTES_PER_MIB) {
            return decimalFormat.format((double) sizeBytes / BYTES_PER_KIB) + " KB";
        }
        return decimalFormat.format((double) sizeBytes / BYTES_PER_MIB) + " MB";
    }
}
