package com.desperadoboi.imagetopdf.document.spreadsheet;

import java.io.File;
import java.io.IOException;

public interface SpreadsheetParser {
    XlsxWorkbook parse(File file) throws IOException;
}
