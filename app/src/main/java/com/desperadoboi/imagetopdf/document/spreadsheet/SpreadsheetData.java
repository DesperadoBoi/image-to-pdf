package com.desperadoboi.imagetopdf.document.spreadsheet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SpreadsheetData {
    private final List<List<String>> rows;
    private final int columnCount;
    private final boolean truncated;
    private final char delimiter;

    public SpreadsheetData(List<List<String>> rows, boolean truncated, char delimiter) {
        ArrayList<List<String>> copiedRows = new ArrayList<>();
        int maximumColumns = 0;
        for (List<String> row : rows) {
            ArrayList<String> copiedRow = new ArrayList<>(row);
            copiedRows.add(Collections.unmodifiableList(copiedRow));
            maximumColumns = Math.max(maximumColumns, copiedRow.size());
        }
        this.rows = Collections.unmodifiableList(copiedRows);
        this.columnCount = maximumColumns;
        this.truncated = truncated;
        this.delimiter = delimiter;
    }

    public List<List<String>> getRows() { return rows; }
    public int getColumnCount() { return columnCount; }
    public boolean isTruncated() { return truncated; }
    public char getDelimiter() { return delimiter; }
}
