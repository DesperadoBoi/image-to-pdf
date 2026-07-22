package com.desperadoboi.imagetopdf.document.spreadsheet;

import org.junit.Test;

import java.io.StringReader;

import static org.junit.Assert.assertEquals;

public class CsvParserTest {
    private final CsvParser parser = new CsvParser();

    @Test
    public void parsesCommasQuotesEscapedQuotesAndEmptyCells() throws Exception {
        SpreadsheetData data = parser.parse(new StringReader(
                "name,note,empty\nAlice,\"hello, world\",\nBob,\"said \"\"yes\"\"\",x"
        ), null);

        assertEquals("hello, world", data.getRows().get(1).get(1));
        assertEquals("", data.getRows().get(1).get(2));
        assertEquals("said \"yes\"", data.getRows().get(2).get(1));
    }

    @Test
    public void parsesMultilineCells() throws Exception {
        SpreadsheetData data = parser.parse(new StringReader("a,b\n1,\"two\nlines\""), ',');
        assertEquals("two\nlines", data.getRows().get(1).get(1));
    }

    @Test
    public void detectsSemicolonAndTab() throws Exception {
        assertEquals(';', parser.parse(new StringReader("a;b\n1;2"), null).getDelimiter());
        assertEquals('\t', parser.parse(new StringReader("a\tb\n1\t2"), null).getDelimiter());
    }

    @Test
    public void skipsUtf8BomAndKeepsUnevenRows() throws Exception {
        SpreadsheetData data = parser.parse(new StringReader("\uFEFFa,b\n1"), null);
        assertEquals("a", data.getRows().get(0).get(0));
        assertEquals(1, data.getRows().get(1).size());
    }
}
