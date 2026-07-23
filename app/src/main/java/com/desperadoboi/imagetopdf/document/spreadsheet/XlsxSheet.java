package com.desperadoboi.imagetopdf.document.spreadsheet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class XlsxSheet {
    private final String name;
    private final SpreadsheetData data;
    private final String usedRange;
    private final List<String> mergedRanges;
    private final SpreadsheetSheetLayout layout;

    public XlsxSheet(
            String name,
            SpreadsheetData data,
            String usedRange,
            List<String> mergedRanges
    ) {
        this(
                name,
                data,
                usedRange,
                mergedRanges,
                SpreadsheetSheetLayout.empty(data.getRows().size(), data.getColumnCount())
        );
    }

    public XlsxSheet(
            String name,
            SpreadsheetData data,
            String usedRange,
            List<String> mergedRanges,
            SpreadsheetSheetLayout layout
    ) {
        this.name = name;
        this.data = data;
        this.usedRange = usedRange;
        this.mergedRanges = Collections.unmodifiableList(new ArrayList<>(mergedRanges));
        this.layout = layout;
    }

    public String getName() {
        return name;
    }

    public SpreadsheetData getData() {
        return data;
    }

    public String getUsedRange() {
        return usedRange;
    }

    public List<String> getMergedRanges() {
        return mergedRanges;
    }

    public SpreadsheetSheetLayout getLayout() {
        return layout;
    }
}
