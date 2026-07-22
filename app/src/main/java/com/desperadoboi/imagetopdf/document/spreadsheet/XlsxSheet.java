package com.desperadoboi.imagetopdf.document.spreadsheet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class XlsxSheet {
    private final String name;
    private final SpreadsheetData data;
    private final String usedRange;
    private final List<String> mergedRanges;

    public XlsxSheet(
            String name,
            SpreadsheetData data,
            String usedRange,
            List<String> mergedRanges
    ) {
        this.name = name;
        this.data = data;
        this.usedRange = usedRange;
        this.mergedRanges = Collections.unmodifiableList(new ArrayList<>(mergedRanges));
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
}
