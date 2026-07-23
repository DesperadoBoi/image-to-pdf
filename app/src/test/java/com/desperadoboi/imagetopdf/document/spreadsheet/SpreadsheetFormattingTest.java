package com.desperadoboi.imagetopdf.document.spreadsheet;

import org.junit.Test;

import java.util.Date;

import static org.junit.Assert.assertEquals;

public class SpreadsheetFormattingTest {
    private final SpreadsheetValueFormatter formatter = new SpreadsheetValueFormatter();

    @Test
    public void formatsColumnLabels() {
        assertEquals("A", ColumnLabelFormatter.format(0));
        assertEquals("Z", ColumnLabelFormatter.format(25));
        assertEquals("AA", ColumnLabelFormatter.format(26));
        assertEquals("AB", ColumnLabelFormatter.format(27));
    }

    @Test
    public void formatsSupportedCellKinds() {
        assertEquals("7", formatter.format(SpreadsheetCellValue.number(7)));
        assertEquals("7.25", formatter.format(SpreadsheetCellValue.number(7.25)));
        assertEquals("1970-01-01", formatter.format(SpreadsheetCellValue.date(new Date(0))));
        assertEquals("TRUE", formatter.format(SpreadsheetCellValue.bool(true)));
        assertEquals("cached", formatter.format(SpreadsheetCellValue.formulaCached("cached")));
        assertEquals("", formatter.format(SpreadsheetCellValue.blank()));
        assertEquals("#VALUE!", formatter.format(SpreadsheetCellValue.error("#VALUE!")));
    }
}
