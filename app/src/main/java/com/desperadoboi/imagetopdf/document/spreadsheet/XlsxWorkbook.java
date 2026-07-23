package com.desperadoboi.imagetopdf.document.spreadsheet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class XlsxWorkbook {
    private final List<XlsxSheet> sheets;
    private final boolean date1904;
    private final boolean truncated;

    public XlsxWorkbook(List<XlsxSheet> sheets, boolean date1904, boolean truncated) {
        this.sheets = Collections.unmodifiableList(new ArrayList<>(sheets));
        this.date1904 = date1904;
        this.truncated = truncated;
    }

    public List<XlsxSheet> getSheets() {
        return sheets;
    }

    public boolean usesDate1904() {
        return date1904;
    }

    public boolean isTruncated() {
        return truncated;
    }
}
