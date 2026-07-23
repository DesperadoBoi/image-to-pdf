package com.desperadoboi.imagetopdf.document.spreadsheet;

import com.desperadoboi.imagetopdf.document.DocumentLimits;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

public final class CsvParser {
    private static final int DELIMITER_SAMPLE_CHARS = 16 * 1024;

    public SpreadsheetData parse(Reader source, Character requestedDelimiter) throws IOException {
        BufferedReader reader = source instanceof BufferedReader
                ? (BufferedReader) source
                : new BufferedReader(source);
        reader.mark(DELIMITER_SAMPLE_CHARS + 1);
        char[] sampleBuffer = new char[DELIMITER_SAMPLE_CHARS];
        int sampleLength = reader.read(sampleBuffer);
        reader.reset();
        char delimiter = requestedDelimiter == null
                ? detectDelimiter(sampleBuffer, Math.max(0, sampleLength))
                : requestedDelimiter;
        return parseRows(reader, delimiter);
    }

    static char detectDelimiter(char[] sample, int length) {
        int commas = 0;
        int semicolons = 0;
        int tabs = 0;
        boolean quoted = false;
        for (int index = 0; index < length; index++) {
            char value = sample[index];
            if (value == '"') {
                if (quoted && index + 1 < length && sample[index + 1] == '"') {
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (!quoted) {
                if (value == ',') commas++;
                if (value == ';') semicolons++;
                if (value == '\t') tabs++;
            }
        }
        if (tabs >= commas && tabs >= semicolons && tabs > 0) return '\t';
        if (semicolons > commas) return ';';
        return ',';
    }

    private SpreadsheetData parseRows(BufferedReader reader, char delimiter) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        boolean truncated = false;
        boolean firstCharacter = true;
        int characterCount = 0;
        int value;

        while ((value = reader.read()) != -1) {
            char current = (char) value;
            if (firstCharacter && current == '\uFEFF') {
                firstCharacter = false;
                continue;
            }
            firstCharacter = false;
            characterCount++;
            if (characterCount > DocumentLimits.MAX_TEXT_PREVIEW_CHARS) {
                truncated = true;
                break;
            }
            if (quoted) {
                if (current == '"') {
                    reader.mark(1);
                    int next = reader.read();
                    if (next == '"') {
                        appendBounded(cell, '"');
                        characterCount++;
                    } else {
                        quoted = false;
                        if (next != -1) reader.reset();
                    }
                } else {
                    appendBounded(cell, current);
                }
                continue;
            }
            if (current == '"' && cell.length() == 0) {
                quoted = true;
            } else if (current == delimiter) {
                addCell(row, cell);
            } else if (current == '\n') {
                addCell(row, cell);
                addRow(rows, row);
                row = new ArrayList<>();
                if (rows.size() >= DocumentLimits.MAX_SPREADSHEET_ROWS) {
                    truncated = reader.read() != -1;
                    break;
                }
            } else if (current != '\r') {
                appendBounded(cell, current);
            }
        }
        if (quoted) {
            throw new IOException("Unterminated quoted field");
        }
        if (!row.isEmpty() || cell.length() > 0) {
            addCell(row, cell);
            if (rows.size() < DocumentLimits.MAX_SPREADSHEET_ROWS) {
                addRow(rows, row);
            } else {
                truncated = true;
            }
        }
        return new SpreadsheetData(rows, truncated, delimiter);
    }

    private void addCell(List<String> row, StringBuilder cell) {
        if (row.size() < DocumentLimits.MAX_SPREADSHEET_COLUMNS) {
            row.add(cell.toString());
        }
        cell.setLength(0);
    }

    private void addRow(List<List<String>> rows, List<String> row) {
        rows.add(row);
    }

    private void appendBounded(StringBuilder cell, char value) {
        if (cell.length() < DocumentLimits.MAX_CELL_CHARS) {
            cell.append(value);
        }
    }
}
