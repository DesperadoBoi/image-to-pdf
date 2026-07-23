package com.desperadoboi.imagetopdf.document.spreadsheet;

public final class ColumnLabelFormatter {
    private ColumnLabelFormatter() {
    }

    public static String format(int zeroBasedIndex) {
        if (zeroBasedIndex < 0) {
            throw new IllegalArgumentException("Column index must not be negative");
        }
        StringBuilder label = new StringBuilder();
        int remaining = zeroBasedIndex;
        do {
            label.append((char) ('A' + remaining % 26));
            remaining = (remaining / 26) - 1;
        } while (remaining >= 0);
        return label.reverse().toString();
    }
}
