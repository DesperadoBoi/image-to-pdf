package com.desperadoboi.imagetopdf.document.spreadsheet;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public final class SpreadsheetValueFormatter {
    private final DecimalFormat decimalFormat;
    private final SimpleDateFormat dateFormat;

    public SpreadsheetValueFormatter() {
        decimalFormat = new DecimalFormat("0.##########", DecimalFormatSymbols.getInstance(Locale.US));
        dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    public String format(SpreadsheetCellValue cellValue) {
        if (cellValue == null || cellValue.getKind() == SpreadsheetCellValue.Kind.BLANK) {
            return "";
        }
        Object value = cellValue.getValue();
        if (value == null) return "";
        if (value instanceof Number) return decimalFormat.format(((Number) value).doubleValue());
        if (value instanceof Date) return dateFormat.format((Date) value);
        if (value instanceof Boolean) return Boolean.TRUE.equals(value) ? "TRUE" : "FALSE";
        return String.valueOf(value);
    }
}
