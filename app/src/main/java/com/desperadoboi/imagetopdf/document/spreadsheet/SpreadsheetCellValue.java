package com.desperadoboi.imagetopdf.document.spreadsheet;

import java.util.Date;

public final class SpreadsheetCellValue {
    public enum Kind { STRING, NUMBER, DATE, BOOLEAN, FORMULA_CACHED, BLANK, ERROR }

    private final Kind kind;
    private final Object value;

    private SpreadsheetCellValue(Kind kind, Object value) {
        this.kind = kind;
        this.value = value;
    }

    public static SpreadsheetCellValue string(String value) {
        return new SpreadsheetCellValue(Kind.STRING, value);
    }
    public static SpreadsheetCellValue number(double value) {
        return new SpreadsheetCellValue(Kind.NUMBER, value);
    }
    public static SpreadsheetCellValue date(Date value) {
        return new SpreadsheetCellValue(Kind.DATE, value);
    }
    public static SpreadsheetCellValue bool(boolean value) {
        return new SpreadsheetCellValue(Kind.BOOLEAN, value);
    }
    public static SpreadsheetCellValue formulaCached(Object value) {
        return new SpreadsheetCellValue(Kind.FORMULA_CACHED, value);
    }
    public static SpreadsheetCellValue blank() {
        return new SpreadsheetCellValue(Kind.BLANK, null);
    }
    public static SpreadsheetCellValue error(String value) {
        return new SpreadsheetCellValue(Kind.ERROR, value);
    }

    public Kind getKind() { return kind; }
    public Object getValue() { return value; }
}
