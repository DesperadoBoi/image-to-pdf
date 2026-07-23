package com.desperadoboi.imagetopdf.document.spreadsheet;

import com.desperadoboi.imagetopdf.document.DocumentLimits;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class XlsxSpreadsheetParser implements SpreadsheetParser {
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    @Override
    public XlsxWorkbook parse(File file) throws IOException {
        XlsxPackageInspector.Inspection inspection = XlsxPackageInspector.inspect(file);
        if (!inspection.isXlsx()) {
            throw corrupted("The ZIP package is not an XLSX workbook", null);
        }
        try (ZipFile zipFile = new ZipFile(file)) {
            Map<String, ZipEntry> entries = entriesByName(zipFile);
            WorkbookMetadata workbookMetadata = readWorkbook(
                    zipFile,
                    requiredEntry(entries, "xl/workbook.xml")
            );
            Relationships relationships = readRelationships(
                    zipFile,
                    requiredEntry(entries, "xl/_rels/workbook.xml.rels"),
                    "xl/workbook.xml"
            );
            SharedStrings sharedStrings = readSharedStrings(
                    zipFile,
                    optionalRelatedEntry(entries, relationships, "/sharedStrings",
                            "xl/sharedStrings.xml")
            );
            Styles styles = readStyles(
                    zipFile,
                    optionalRelatedEntry(entries, relationships, "/styles", "xl/styles.xml")
            );

            List<XlsxSheet> sheets = new ArrayList<>();
            CellCounter counter = new CellCounter();
            boolean truncated = workbookMetadata.truncated || sharedStrings.truncated;
            for (SheetReference sheetReference : workbookMetadata.sheets) {
                Relationship relationship = relationships.byId.get(sheetReference.relationshipId);
                if (relationship == null || !relationship.type.endsWith("/worksheet")) {
                    throw corrupted("Worksheet relationship is missing", null);
                }
                if (relationship.external) {
                    throw unsupported("External worksheets are not supported");
                }
                ZipEntry sheetEntry = entries.get(relationship.target);
                if (sheetEntry == null) {
                    throw corrupted("Worksheet part is missing", null);
                }
                SheetParseResult result = readWorksheet(
                        zipFile,
                        sheetEntry,
                        sheetReference.name,
                        sharedStrings,
                        styles,
                        workbookMetadata.date1904,
                        counter
                );
                sheets.add(result.sheet);
                truncated |= result.truncated;
            }
            if (sheets.isEmpty()) {
                throw corrupted("Workbook does not contain worksheets", null);
            }
            return new XlsxWorkbook(sheets, workbookMetadata.date1904, truncated);
        } catch (XlsxParseException exception) {
            throw exception;
        } catch (XmlPullParserException | RuntimeException exception) {
            throw corrupted("Unable to parse XLSX workbook", exception);
        }
    }

    private Map<String, ZipEntry> entriesByName(ZipFile zipFile) {
        Map<String, ZipEntry> entries = new HashMap<>();
        Enumeration<? extends ZipEntry> enumeration = zipFile.entries();
        while (enumeration.hasMoreElements()) {
            ZipEntry entry = enumeration.nextElement();
            entries.put(entry.getName(), entry);
        }
        return entries;
    }

    private WorkbookMetadata readWorkbook(ZipFile zipFile, ZipEntry entry)
            throws IOException, XmlPullParserException, XlsxParseException {
        List<SheetReference> sheets = new ArrayList<>();
        boolean date1904 = false;
        boolean truncated = false;
        try (InputStream inputStream = zipFile.getInputStream(entry)) {
            XmlPullParser parser = XlsxXml.newParser(inputStream);
            XlsxXml.Budget budget = new XlsxXml.Budget();
            int event;
            while ((event = XlsxXml.next(parser, budget)) != XmlPullParser.END_DOCUMENT) {
                if (event != XmlPullParser.START_TAG) continue;
                if ("workbookPr".equals(parser.getName())) {
                    date1904 = parseBoolean(XlsxXml.attribute(parser, "date1904"));
                } else if ("sheet".equals(parser.getName())) {
                    String name = XlsxXml.attribute(parser, "name");
                    String relationshipId = XlsxXml.attribute(parser, "id");
                    if (name == null || name.trim().isEmpty() || relationshipId == null) {
                        throw corrupted("Worksheet metadata is incomplete", null);
                    }
                    if (sheets.size() < DocumentLimits.MAX_XLSX_SHEETS) {
                        sheets.add(new SheetReference(limit(name.trim(), 128), relationshipId));
                    } else {
                        truncated = true;
                    }
                }
            }
        }
        return new WorkbookMetadata(sheets, date1904, truncated);
    }

    private Relationships readRelationships(
            ZipFile zipFile,
            ZipEntry entry,
            String sourcePart
    ) throws IOException, XmlPullParserException, XlsxParseException {
        Map<String, Relationship> byId = new HashMap<>();
        List<Relationship> all = new ArrayList<>();
        try (InputStream inputStream = zipFile.getInputStream(entry)) {
            XmlPullParser parser = XlsxXml.newParser(inputStream);
            XlsxXml.Budget budget = new XlsxXml.Budget();
            int event;
            while ((event = XlsxXml.next(parser, budget)) != XmlPullParser.END_DOCUMENT) {
                if (event != XmlPullParser.START_TAG
                        || !"Relationship".equals(parser.getName())) continue;
                String id = XlsxXml.attribute(parser, "Id");
                String type = XlsxXml.attribute(parser, "Type");
                String targetMode = XlsxXml.attribute(parser, "TargetMode");
                boolean external = "External".equalsIgnoreCase(targetMode);
                String target = external
                        ? null
                        : XlsxPackageInspector.normalizeRelationshipTarget(
                                sourcePart,
                                XlsxXml.attribute(parser, "Target")
                        );
                if (id == null || type == null || (!external && target == null)) {
                    throw corrupted("Workbook relationship is invalid", null);
                }
                Relationship relationship = new Relationship(type, target, external);
                if (byId.put(id, relationship) != null) {
                    throw corrupted("Duplicate workbook relationship", null);
                }
                all.add(relationship);
            }
        }
        return new Relationships(byId, all);
    }

    private SharedStrings readSharedStrings(ZipFile zipFile, ZipEntry entry)
            throws IOException, XmlPullParserException, XlsxParseException {
        if (entry == null) return new SharedStrings(Collections.emptyList(), false);
        List<String> values = new ArrayList<>();
        boolean truncated = false;
        int totalCharacters = 0;
        try (InputStream inputStream = zipFile.getInputStream(entry)) {
            XmlPullParser parser = XlsxXml.newParser(inputStream);
            XlsxXml.Budget budget = new XlsxXml.Budget();
            int event;
            while ((event = XlsxXml.next(parser, budget)) != XmlPullParser.END_DOCUMENT) {
                if (event != XmlPullParser.START_TAG || !"si".equals(parser.getName())) continue;
                RichText text = readRichText(parser, budget, "si");
                if (values.size() >= DocumentLimits.MAX_SHARED_STRINGS
                        || totalCharacters >= DocumentLimits.MAX_SHARED_STRING_CHARS) {
                    truncated = true;
                    break;
                }
                int remaining = DocumentLimits.MAX_SHARED_STRING_CHARS - totalCharacters;
                String value = limit(text.value, Math.min(DocumentLimits.MAX_CELL_CHARS, remaining));
                truncated |= text.truncated || value.length() < text.value.length();
                values.add(value);
                totalCharacters += value.length();
            }
        }
        return new SharedStrings(values, truncated);
    }

    private Styles readStyles(ZipFile zipFile, ZipEntry entry)
            throws IOException, XmlPullParserException, XlsxParseException {
        if (entry == null) return Styles.empty();
        Map<Integer, String> formats = new HashMap<>();
        List<FontStyle> fonts = new ArrayList<>();
        List<Integer> fills = new ArrayList<>();
        List<BorderStyle> borders = new ArrayList<>();
        List<CellFormat> cellFormats = new ArrayList<>();
        String section = "";
        int sectionDepth = -1;
        try (InputStream inputStream = zipFile.getInputStream(entry)) {
            XmlPullParser parser = XlsxXml.newParser(inputStream);
            XlsxXml.Budget budget = new XlsxXml.Budget();
            int event;
            while ((event = XlsxXml.next(parser, budget)) != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    if ("numFmt".equals(parser.getName())) {
                        Integer id = parseInteger(XlsxXml.attribute(parser, "numFmtId"));
                        String code = XlsxXml.attribute(parser, "formatCode");
                        if (id != null && code != null) formats.put(id, code);
                    } else if ("fonts".equals(parser.getName())
                            || "fills".equals(parser.getName())
                            || "borders".equals(parser.getName())
                            || "cellXfs".equals(parser.getName())) {
                        section = parser.getName();
                        sectionDepth = parser.getDepth();
                    } else if (parser.getDepth() == sectionDepth + 1) {
                        if ("fonts".equals(section) && "font".equals(parser.getName())) {
                            fonts.add(readFont(parser, budget));
                        } else if ("fills".equals(section) && "fill".equals(parser.getName())) {
                            fills.add(readFill(parser, budget));
                        } else if ("borders".equals(section)
                                && "border".equals(parser.getName())) {
                            borders.add(readBorder(parser, budget));
                        } else if ("cellXfs".equals(section) && "xf".equals(parser.getName())) {
                            cellFormats.add(readCellFormat(parser, budget));
                        }
                    }
                } else if (event == XmlPullParser.END_TAG
                        && parser.getDepth() == sectionDepth
                        && section.equals(parser.getName())) {
                    section = "";
                    sectionDepth = -1;
                }
            }
        }
        List<ResolvedStyle> resolvedStyles = new ArrayList<>();
        for (int styleIndex = 0; styleIndex < cellFormats.size(); styleIndex++) {
            CellFormat format = cellFormats.get(styleIndex);
            FontStyle font = getOrDefault(fonts, format.fontId, FontStyle.DEFAULT);
            Integer fill = getOrDefault(fills, format.fillId, null);
            BorderStyle border = getOrDefault(borders, format.borderId, BorderStyle.NONE);
            SpreadsheetCellStyle visual = new SpreadsheetCellStyle.Builder()
                    .setStyleId(styleIndex + 1)
                    .setFillColor(fill)
                    .setFontColor(font.color)
                    .setBold(font.bold)
                    .setItalic(font.italic)
                    .setUnderline(font.underline)
                    .setFontSizePoints(font.sizePoints)
                    .setHorizontalAlignment(format.horizontalAlignment)
                    .setVerticalAlignment(format.verticalAlignment)
                    .setWrapText(format.wrapText)
                    .setBorders(border.left, border.top, border.right, border.bottom)
                    .build();
            resolvedStyles.add(new ResolvedStyle(
                    dateStyle(format.numberFormatId, formats.get(format.numberFormatId)),
                    visual
            ));
        }
        return new Styles(resolvedStyles);
    }

    private FontStyle readFont(XmlPullParser parser, XlsxXml.Budget budget)
            throws IOException, XmlPullParserException, XlsxParseException {
        int depth = parser.getDepth();
        boolean bold = false;
        boolean italic = false;
        boolean underline = false;
        float sizePoints = 0f;
        Integer color = null;
        int event;
        while ((event = XlsxXml.next(parser, budget)) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                String element = parser.getName();
                if ("b".equals(element)) bold = enabledProperty(parser);
                else if ("i".equals(element)) italic = enabledProperty(parser);
                else if ("u".equals(element)) underline = enabledProperty(parser);
                else if ("sz".equals(element)) {
                    sizePoints = parsePositiveFloat(XlsxXml.attribute(parser, "val"), 0f);
                } else if ("color".equals(element)) {
                    color = parseColor(parser);
                }
            } else if (event == XmlPullParser.END_TAG
                    && "font".equals(parser.getName())
                    && parser.getDepth() == depth) {
                break;
            }
        }
        return new FontStyle(bold, italic, underline, sizePoints, color);
    }

    private Integer readFill(XmlPullParser parser, XlsxXml.Budget budget)
            throws IOException, XmlPullParserException, XlsxParseException {
        int depth = parser.getDepth();
        String patternType = "";
        Integer foreground = null;
        Integer background = null;
        int event;
        while ((event = XlsxXml.next(parser, budget)) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                if ("patternFill".equals(parser.getName())) {
                    String value = XlsxXml.attribute(parser, "patternType");
                    patternType = value == null ? "" : value;
                } else if ("fgColor".equals(parser.getName())) {
                    foreground = parseColor(parser);
                } else if ("bgColor".equals(parser.getName())) {
                    background = parseColor(parser);
                }
            } else if (event == XmlPullParser.END_TAG
                    && "fill".equals(parser.getName())
                    && parser.getDepth() == depth) {
                break;
            }
        }
        if ("none".equals(patternType) || "gray125".equals(patternType)) return null;
        return foreground != null ? foreground : background;
    }

    private BorderStyle readBorder(XmlPullParser parser, XlsxXml.Budget budget)
            throws IOException, XmlPullParserException, XlsxParseException {
        int depth = parser.getDepth();
        SpreadsheetBorder left = SpreadsheetBorder.NONE;
        SpreadsheetBorder top = SpreadsheetBorder.NONE;
        SpreadsheetBorder right = SpreadsheetBorder.NONE;
        SpreadsheetBorder bottom = SpreadsheetBorder.NONE;
        int event;
        while ((event = XlsxXml.next(parser, budget)) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.getDepth() == depth + 1) {
                String element = parser.getName();
                if ("left".equals(element)) left = readBorderSide(parser, budget, element);
                else if ("top".equals(element)) top = readBorderSide(parser, budget, element);
                else if ("right".equals(element)) right = readBorderSide(parser, budget, element);
                else if ("bottom".equals(element)) bottom = readBorderSide(parser, budget, element);
            } else if (event == XmlPullParser.END_TAG
                    && "border".equals(parser.getName())
                    && parser.getDepth() == depth) {
                break;
            }
        }
        return new BorderStyle(left, top, right, bottom);
    }

    private SpreadsheetBorder readBorderSide(
            XmlPullParser parser,
            XlsxXml.Budget budget,
            String sideElement
    ) throws IOException, XmlPullParserException, XlsxParseException {
        int depth = parser.getDepth();
        SpreadsheetBorder.Style style = borderStyle(XlsxXml.attribute(parser, "style"));
        Integer color = null;
        int event;
        while ((event = XlsxXml.next(parser, budget)) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && "color".equals(parser.getName())) {
                color = parseColor(parser);
            } else if (event == XmlPullParser.END_TAG
                    && sideElement.equals(parser.getName())
                    && parser.getDepth() == depth) {
                break;
            }
        }
        if (style == SpreadsheetBorder.Style.NONE) return SpreadsheetBorder.NONE;
        return new SpreadsheetBorder(style, color == null ? 0xFF606060 : color);
    }

    private CellFormat readCellFormat(XmlPullParser parser, XlsxXml.Budget budget)
            throws IOException, XmlPullParserException, XlsxParseException {
        int depth = parser.getDepth();
        int numberFormatId = parseStyleIndex(XlsxXml.attribute(parser, "numFmtId"));
        int fontId = parseStyleIndex(XlsxXml.attribute(parser, "fontId"));
        int fillId = parseStyleIndex(XlsxXml.attribute(parser, "fillId"));
        int borderId = parseStyleIndex(XlsxXml.attribute(parser, "borderId"));
        SpreadsheetCellStyle.HorizontalAlignment horizontal =
                SpreadsheetCellStyle.HorizontalAlignment.GENERAL;
        SpreadsheetCellStyle.VerticalAlignment vertical =
                SpreadsheetCellStyle.VerticalAlignment.BOTTOM;
        boolean wrapText = false;
        int event;
        while ((event = XlsxXml.next(parser, budget)) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && "alignment".equals(parser.getName())) {
                horizontal = horizontalAlignment(XlsxXml.attribute(parser, "horizontal"));
                vertical = verticalAlignment(XlsxXml.attribute(parser, "vertical"));
                wrapText = parseBoolean(XlsxXml.attribute(parser, "wrapText"));
            } else if (event == XmlPullParser.END_TAG
                    && "xf".equals(parser.getName())
                    && parser.getDepth() == depth) {
                break;
            }
        }
        return new CellFormat(
                Math.max(0, numberFormatId),
                fontId,
                fillId,
                borderId,
                horizontal,
                vertical,
                wrapText
        );
    }

    private Integer parseColor(XmlPullParser parser) {
        String rgb = XlsxXml.attribute(parser, "rgb");
        if (rgb != null) {
            String normalized = rgb.trim();
            if (normalized.length() == 6) normalized = "FF" + normalized;
            if (normalized.length() == 8) {
                try {
                    int parsed = (int) Long.parseLong(normalized, 16);
                    return 0xFF000000 | (parsed & 0x00FFFFFF);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        Integer indexed = parseInteger(XlsxXml.attribute(parser, "indexed"));
        if (indexed != null) return indexedColor(indexed);
        Integer theme = parseInteger(XlsxXml.attribute(parser, "theme"));
        return theme == null ? null : themeColor(theme);
    }

    private Integer indexedColor(int index) {
        int[] colors = {
                0xFF000000, 0xFFFFFFFF, 0xFFFF0000, 0xFF00FF00,
                0xFF0000FF, 0xFFFFFF00, 0xFFFF00FF, 0xFF00FFFF,
                0xFF000000, 0xFFFFFFFF, 0xFFFF0000, 0xFF00FF00,
                0xFF0000FF, 0xFFFFFF00, 0xFFFF00FF, 0xFF00FFFF,
                0xFF800000, 0xFF008000, 0xFF000080, 0xFF808000,
                0xFF800080, 0xFF008080, 0xFFC0C0C0, 0xFF808080,
                0xFF9999FF, 0xFF993366, 0xFFFFFFCC, 0xFFCCFFFF,
                0xFF660066, 0xFFFF8080, 0xFF0066CC, 0xFFCCCCFF,
                0xFF000080, 0xFFFF00FF, 0xFFFFFF00, 0xFF00FFFF,
                0xFF800080, 0xFF800000, 0xFF008080, 0xFF0000FF,
                0xFF00CCFF, 0xFFCCFFFF, 0xFFCCFFCC, 0xFFFFFF99,
                0xFF99CCFF, 0xFFFF99CC, 0xFFCC99FF, 0xFFFFCC99,
                0xFF3366FF, 0xFF33CCCC, 0xFF99CC00, 0xFFFFCC00,
                0xFFFF9900, 0xFFFF6600, 0xFF666699, 0xFF969696,
                0xFF003366, 0xFF339966, 0xFF003300, 0xFF333300,
                0xFF993300, 0xFF993366, 0xFF333399, 0xFF333333
        };
        return index >= 0 && index < colors.length ? colors[index] : null;
    }

    private Integer themeColor(int index) {
        int[] colors = {
                0xFFFFFFFF, 0xFF000000, 0xFFEEECE1, 0xFF1F497D,
                0xFF4F81BD, 0xFFC0504D, 0xFF9BBB59, 0xFF8064A2,
                0xFF4BACC6, 0xFFF79646, 0xFF0000FF, 0xFF800080
        };
        return index >= 0 && index < colors.length ? colors[index] : null;
    }

    private SpreadsheetBorder.Style borderStyle(String value) {
        if (value == null || value.isEmpty()) return SpreadsheetBorder.Style.NONE;
        if ("hair".equals(value)) return SpreadsheetBorder.Style.HAIR;
        if ("medium".equals(value) || value.startsWith("medium")) {
            return SpreadsheetBorder.Style.MEDIUM;
        }
        if ("thick".equals(value)) return SpreadsheetBorder.Style.THICK;
        if ("dashed".equals(value) || value.endsWith("Dash")) {
            return SpreadsheetBorder.Style.DASHED;
        }
        if ("dotted".equals(value) || value.endsWith("Dot")) {
            return SpreadsheetBorder.Style.DOTTED;
        }
        if ("double".equals(value)) return SpreadsheetBorder.Style.DOUBLE;
        return SpreadsheetBorder.Style.THIN;
    }

    private SpreadsheetCellStyle.HorizontalAlignment horizontalAlignment(String value) {
        if ("left".equals(value)) return SpreadsheetCellStyle.HorizontalAlignment.LEFT;
        if ("center".equals(value) || "centerContinuous".equals(value)) {
            return SpreadsheetCellStyle.HorizontalAlignment.CENTER;
        }
        if ("right".equals(value)) return SpreadsheetCellStyle.HorizontalAlignment.RIGHT;
        if ("justify".equals(value) || "distributed".equals(value)) {
            return SpreadsheetCellStyle.HorizontalAlignment.JUSTIFY;
        }
        return SpreadsheetCellStyle.HorizontalAlignment.GENERAL;
    }

    private SpreadsheetCellStyle.VerticalAlignment verticalAlignment(String value) {
        if ("top".equals(value)) return SpreadsheetCellStyle.VerticalAlignment.TOP;
        if ("center".equals(value)) return SpreadsheetCellStyle.VerticalAlignment.CENTER;
        return SpreadsheetCellStyle.VerticalAlignment.BOTTOM;
    }

    private boolean enabledProperty(XmlPullParser parser) {
        String value = XlsxXml.attribute(parser, "val");
        return value == null || parseBoolean(value);
    }

    private <T> T getOrDefault(List<T> values, int index, T fallback) {
        return index >= 0 && index < values.size() ? values.get(index) : fallback;
    }

    private SheetParseResult readWorksheet(
            ZipFile zipFile,
            ZipEntry entry,
            String name,
            SharedStrings sharedStrings,
            Styles styles,
            boolean date1904,
            CellCounter counter
    ) throws IOException, XmlPullParserException, XlsxParseException {
        List<List<String>> rows = new ArrayList<>();
        List<String> mergedRanges = new ArrayList<>();
        List<SpreadsheetMergedRange> parsedMergedRanges = new ArrayList<>();
        Map<Long, SpreadsheetCellStyle> cellStyles = new HashMap<>();
        float[] columnWidths = new float[DocumentLimits.MAX_SPREADSHEET_COLUMNS];
        float[] rowHeights = new float[DocumentLimits.MAX_SPREADSHEET_ROWS];
        Arrays.fill(columnWidths, -1f);
        Arrays.fill(rowHeights, -1f);
        int[] columnStyleIndexes = new int[DocumentLimits.MAX_SPREADSHEET_COLUMNS];
        Arrays.fill(columnStyleIndexes, -1);
        boolean truncated = false;
        String usedRange = "";
        float defaultColumnWidth = SpreadsheetSheetLayout.DEFAULT_COLUMN_WIDTH_CHARACTERS;
        float defaultRowHeight = SpreadsheetSheetLayout.DEFAULT_ROW_HEIGHT_POINTS;
        int currentRow = -1;
        int currentRowStyle = -1;
        int implicitRow = -1;
        int implicitColumn = -1;
        int maximumDefinedColumn = -1;
        int maximumDefinedRow = -1;
        try (InputStream inputStream = zipFile.getInputStream(entry)) {
            XmlPullParser parser = XlsxXml.newParser(inputStream);
            XlsxXml.Budget budget = new XlsxXml.Budget();
            int event;
            while ((event = XlsxXml.next(parser, budget)) != XmlPullParser.END_DOCUMENT) {
                if (event != XmlPullParser.START_TAG) continue;
                String element = parser.getName();
                if ("dimension".equals(element)) {
                    String reference = XlsxXml.attribute(parser, "ref");
                    if (reference != null) usedRange = limit(reference, 128);
                } else if ("sheetFormatPr".equals(element)) {
                    defaultColumnWidth = parsePositiveFloat(
                            XlsxXml.attribute(parser, "defaultColWidth"),
                            defaultColumnWidth
                    );
                    defaultRowHeight = parsePositiveFloat(
                            XlsxXml.attribute(parser, "defaultRowHeight"),
                            defaultRowHeight
                    );
                } else if ("col".equals(element)) {
                    Integer first = parseInteger(XlsxXml.attribute(parser, "min"));
                    Integer last = parseInteger(XlsxXml.attribute(parser, "max"));
                    float width = parsePositiveFloat(XlsxXml.attribute(parser, "width"), -1f);
                    boolean hidden = parseBoolean(XlsxXml.attribute(parser, "hidden"));
                    Integer styleIndex = parseInteger(XlsxXml.attribute(parser, "style"));
                    if (first != null && last != null && first > 0 && last >= first) {
                        int from = Math.min(
                                DocumentLimits.MAX_SPREADSHEET_COLUMNS - 1,
                                first - 1
                        );
                        int to = Math.min(
                                DocumentLimits.MAX_SPREADSHEET_COLUMNS - 1,
                                last - 1
                        );
                        for (int column = from; column <= to; column++) {
                            if (hidden) columnWidths[column] = 0f;
                            else if (width > 0f) columnWidths[column] = width;
                            if (styleIndex != null) columnStyleIndexes[column] = styleIndex;
                        }
                    }
                } else if ("row".equals(element)) {
                    Integer rowNumber = parseInteger(XlsxXml.attribute(parser, "r"));
                    currentRow = rowNumber == null ? implicitRow + 1 : rowNumber - 1;
                    if (currentRow < 0) throw corrupted("Worksheet row index is invalid", null);
                    implicitRow = currentRow;
                    implicitColumn = -1;
                    currentRowStyle = parseStyleIndex(XlsxXml.attribute(parser, "s"));
                    if (currentRow >= DocumentLimits.MAX_SPREADSHEET_ROWS) {
                        truncated = true;
                    } else {
                        rowHeights[currentRow] = parseBoolean(
                                XlsxXml.attribute(parser, "hidden")
                        ) ? 0f : parsePositiveFloat(XlsxXml.attribute(parser, "ht"), -1f);
                    }
                } else if ("c".equals(element)) {
                    counter.count++;
                    if (counter.count > DocumentLimits.MAX_XLSX_PARSED_CELLS) {
                        throw tooLarge("Parsed cell limit exceeded");
                    }
                    CellPosition position = parseCellPosition(
                            XlsxXml.attribute(parser, "r"),
                            currentRow,
                            implicitColumn + 1
                    );
                    implicitColumn = position.column;
                    RawCell rawCell = readCell(parser, budget);
                    if (position.row >= DocumentLimits.MAX_SPREADSHEET_ROWS
                            || position.column >= DocumentLimits.MAX_SPREADSHEET_COLUMNS) {
                        truncated = true;
                        continue;
                    }
                    CellDisplay display = formatCell(
                            rawCell,
                            sharedStrings,
                            styles,
                            date1904
                    );
                    truncated |= display.truncated;
                    putCell(rows, position.row, position.column, display.value);
                    int resolvedStyleIndex = rawCell.styleIndex >= 0
                            ? rawCell.styleIndex
                            : currentRowStyle >= 0
                                    ? currentRowStyle
                                    : columnStyleIndexes[position.column];
                    SpreadsheetCellStyle visualStyle = styles.getVisual(resolvedStyleIndex);
                    if (visualStyle != SpreadsheetCellStyle.DEFAULT) {
                        cellStyles.put(
                                SpreadsheetSheetLayout.cellKey(position.row, position.column),
                                visualStyle
                        );
                    }
                    maximumDefinedColumn = Math.max(maximumDefinedColumn, position.column);
                    maximumDefinedRow = Math.max(maximumDefinedRow, position.row);
                } else if ("mergeCell".equals(element)) {
                    String reference = XlsxXml.attribute(parser, "ref");
                    if (reference != null
                            && mergedRanges.size() < DocumentLimits.MAX_XLSX_MERGED_RANGES) {
                        mergedRanges.add(limit(reference, 128));
                        SpreadsheetMergedRange range = parseMergedRange(reference);
                        if (range != null) {
                            parsedMergedRanges.add(range);
                            maximumDefinedColumn = Math.max(
                                    maximumDefinedColumn,
                                    range.getLastColumn()
                            );
                            maximumDefinedRow = Math.max(maximumDefinedRow, range.getLastRow());
                        }
                    } else if (reference != null) {
                        truncated = true;
                    }
                }
            }
        }
        for (String mergedRange : mergedRanges) applyMergedRange(rows, mergedRange);
        if (usedRange.isEmpty()) usedRange = calculatedRange(rows);
        CellPosition usedRangeEnd = parseRangeEnd(usedRange);
        if (usedRangeEnd != null) {
            maximumDefinedColumn = Math.max(maximumDefinedColumn, Math.min(
                    DocumentLimits.MAX_SPREADSHEET_COLUMNS - 1,
                    usedRangeEnd.column
            ));
            maximumDefinedRow = Math.max(maximumDefinedRow, Math.min(
                    DocumentLimits.MAX_SPREADSHEET_ROWS - 1,
                    usedRangeEnd.row
            ));
            if (usedRangeEnd.column >= DocumentLimits.MAX_SPREADSHEET_COLUMNS
                    || usedRangeEnd.row >= DocumentLimits.MAX_SPREADSHEET_ROWS) {
                truncated = true;
            }
        }
        int rowCount = maximumDefinedRow + 1;
        int columnCount = maximumDefinedColumn + 1;
        while (rows.size() < rowCount) rows.add(new ArrayList<>());
        SpreadsheetData data = new SpreadsheetData(rows, truncated, '\0');
        SpreadsheetSheetLayout layout = new SpreadsheetSheetLayout(
                rowCount,
                columnCount,
                defaultColumnWidth,
                defaultRowHeight,
                columnWidths,
                rowHeights,
                cellStyles,
                parsedMergedRanges
        );
        return new SheetParseResult(
                new XlsxSheet(name, data, usedRange, mergedRanges, layout),
                truncated
        );
    }

    private RawCell readCell(XmlPullParser parser, XlsxXml.Budget budget)
            throws IOException, XmlPullParserException, XlsxParseException {
        String type = XlsxXml.attribute(parser, "t");
        Integer styleIndex = parseInteger(XlsxXml.attribute(parser, "s"));
        int cellDepth = parser.getDepth();
        StringBuilder value = new StringBuilder();
        StringBuilder formula = new StringBuilder();
        StringBuilder inline = new StringBuilder();
        boolean inValue = false;
        boolean inFormula = false;
        boolean inInlineText = false;
        boolean truncated = false;
        int event;
        while ((event = XlsxXml.next(parser, budget)) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                if ("v".equals(parser.getName())) inValue = true;
                if ("f".equals(parser.getName())) inFormula = true;
                if ("t".equals(parser.getName())) inInlineText = true;
            } else if (event == XmlPullParser.END_TAG) {
                if ("v".equals(parser.getName())) inValue = false;
                if ("f".equals(parser.getName())) inFormula = false;
                if ("t".equals(parser.getName())) inInlineText = false;
                if ("c".equals(parser.getName()) && parser.getDepth() == cellDepth) break;
            } else if (event == XmlPullParser.TEXT
                    || event == XmlPullParser.CDSECT
                    || event == XmlPullParser.ENTITY_REF) {
                String text = parser.getText();
                if (text == null) continue;
                if (inValue) truncated |= appendBounded(value, text);
                if (inFormula) truncated |= appendBounded(formula, text);
                if (inInlineText) truncated |= appendBounded(inline, text);
            }
        }
        return new RawCell(type, styleIndex == null ? -1 : styleIndex,
                value.toString(), formula.toString(), inline.toString(), truncated);
    }

    private RichText readRichText(
            XmlPullParser parser,
            XlsxXml.Budget budget,
            String endElement
    ) throws IOException, XmlPullParserException, XlsxParseException {
        int startDepth = parser.getDepth();
        StringBuilder value = new StringBuilder();
        boolean inText = false;
        boolean truncated = false;
        int event;
        while ((event = XlsxXml.next(parser, budget)) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && "t".equals(parser.getName())) {
                inText = true;
            } else if (event == XmlPullParser.END_TAG) {
                if ("t".equals(parser.getName())) inText = false;
                if (endElement.equals(parser.getName()) && parser.getDepth() == startDepth) break;
            } else if (inText && (event == XmlPullParser.TEXT
                    || event == XmlPullParser.CDSECT
                    || event == XmlPullParser.ENTITY_REF)) {
                String text = parser.getText();
                if (text != null) truncated |= appendBounded(value, text);
            }
        }
        return new RichText(value.toString(), truncated);
    }

    private CellDisplay formatCell(
            RawCell cell,
            SharedStrings sharedStrings,
            Styles styles,
            boolean date1904
    ) throws XlsxParseException {
        boolean formula = !cell.formula.isEmpty();
        String raw = "inlineStr".equals(cell.type) ? cell.inlineValue : cell.value;
        boolean truncated = cell.truncated;
        if (formula && raw.isEmpty()) {
            String formulaText = limit("=" + cell.formula, DocumentLimits.MAX_CELL_CHARS);
            return new CellDisplay(formulaText, truncated || formulaText.length() < cell.formula.length() + 1);
        }
        if ("s".equals(cell.type)) {
            Integer index = parseInteger(raw);
            if (index == null || index < 0) throw corrupted("Shared string index is invalid", null);
            if (index >= sharedStrings.values.size()) {
                if (sharedStrings.truncated) return new CellDisplay("", true);
                throw corrupted("Shared string is missing", null);
            }
            return new CellDisplay(sharedStrings.values.get(index), truncated);
        }
        if ("inlineStr".equals(cell.type) || "str".equals(cell.type)) {
            return new CellDisplay(limit(raw), truncated || raw.length() > DocumentLimits.MAX_CELL_CHARS);
        }
        if ("b".equals(cell.type)) {
            return new CellDisplay(("1".equals(raw) || "true".equalsIgnoreCase(raw))
                    ? "TRUE" : "FALSE", truncated);
        }
        if ("e".equals(cell.type)) {
            return new CellDisplay(raw.isEmpty() ? "#ERROR" : limit(raw), truncated);
        }
        if ("d".equals(cell.type)) {
            return new CellDisplay(limit(raw), truncated || raw.length() > DocumentLimits.MAX_CELL_CHARS);
        }
        if (raw.isEmpty()) return new CellDisplay("", truncated);
        DateStyle dateStyle = styles.getDateStyle(cell.styleIndex);
        try {
            BigDecimal number = new BigDecimal(raw.trim());
            if (dateStyle.date) {
                return new CellDisplay(formatExcelDate(number.doubleValue(), date1904, dateStyle), truncated);
            }
            BigDecimal normalized = number.stripTrailingZeros();
            return new CellDisplay(normalized.scale() < 0
                    ? normalized.setScale(0).toPlainString()
                    : normalized.toPlainString(), truncated);
        } catch (NumberFormatException exception) {
            if (formula) return new CellDisplay(limit(raw), true);
            throw corrupted("Numeric cell value is invalid", exception);
        }
    }

    private String formatExcelDate(double serial, boolean date1904, DateStyle style) {
        if (!Double.isFinite(serial) || Math.abs(serial) > 1_000_000D) {
            return BigDecimal.valueOf(serial).stripTrailingZeros().toPlainString();
        }
        long wholeDays = (long) Math.floor(serial);
        double fraction = serial - wholeDays;
        if (!date1904 && wholeDays >= 60L) wholeDays--;
        GregorianCalendar calendar = new GregorianCalendar(UTC, Locale.US);
        calendar.clear();
        calendar.set(date1904 ? 1904 : 1899, date1904 ? 0 : 11, date1904 ? 1 : 31);
        calendar.add(GregorianCalendar.DAY_OF_MONTH, (int) wholeDays);
        calendar.add(GregorianCalendar.MILLISECOND,
                (int) Math.round(fraction * 86_400_000D));
        String pattern = style.hasDate && style.hasTime
                ? "yyyy-MM-dd HH:mm:ss"
                : style.hasTime ? "HH:mm:ss" : "yyyy-MM-dd";
        SimpleDateFormat formatter = new SimpleDateFormat(pattern, Locale.US);
        formatter.setTimeZone(UTC);
        return formatter.format(new Date(calendar.getTimeInMillis()));
    }

    private DateStyle dateStyle(int formatId, String customCode) {
        if (formatId >= 14 && formatId <= 17) return new DateStyle(true, true, false);
        if (formatId >= 18 && formatId <= 21) return new DateStyle(true, false, true);
        if (formatId == 22) return new DateStyle(true, true, true);
        if (formatId >= 27 && formatId <= 36) return new DateStyle(true, true, false);
        if (formatId >= 45 && formatId <= 47) return new DateStyle(true, false, true);
        if (formatId >= 50 && formatId <= 58) return new DateStyle(true, true, false);
        if (customCode == null) return DateStyle.NONE;
        String normalized = normalizeFormatCode(customCode);
        boolean hasDate = containsAny(normalized, 'y', 'd');
        boolean hasTime = containsAny(normalized, 'h', 's')
                || normalized.contains("[h]")
                || normalized.contains("[m]")
                || normalized.contains("[s]");
        boolean month = normalized.indexOf('m') >= 0;
        boolean date = hasDate || hasTime || (month && (normalized.contains("/")
                || normalized.contains("-")));
        return new DateStyle(date, hasDate || (date && !hasTime), hasTime);
    }

    private String normalizeFormatCode(String formatCode) {
        StringBuilder result = new StringBuilder();
        boolean quoted = false;
        boolean escaped = false;
        boolean bracketed = false;
        StringBuilder bracket = new StringBuilder();
        for (int index = 0; index < formatCode.length(); index++) {
            char current = Character.toLowerCase(formatCode.charAt(index));
            if (escaped) {
                escaped = false;
                continue;
            }
            if (current == '\\' || current == '_' || current == '*') {
                escaped = true;
                continue;
            }
            if (current == '"') {
                quoted = !quoted;
                continue;
            }
            if (quoted) continue;
            if (current == '[') {
                bracketed = true;
                bracket.setLength(0);
                continue;
            }
            if (current == ']' && bracketed) {
                bracketed = false;
                String bracketValue = bracket.toString();
                if ("h".equals(bracketValue)
                        || "m".equals(bracketValue)
                        || "s".equals(bracketValue)) {
                    result.append('[').append(bracketValue).append(']');
                }
                continue;
            }
            if (bracketed) {
                bracket.append(current);
            } else {
                result.append(current);
            }
        }
        return result.toString();
    }

    private boolean containsAny(String value, char first, char second) {
        return value.indexOf(first) >= 0 || value.indexOf(second) >= 0;
    }

    private CellPosition parseCellPosition(String reference, int fallbackRow, int fallbackColumn)
            throws XlsxParseException {
        if (reference == null || reference.isEmpty()) {
            if (fallbackRow < 0 || fallbackColumn < 0) {
                throw corrupted("Cell position is missing", null);
            }
            return new CellPosition(fallbackRow, fallbackColumn);
        }
        int split = 0;
        long column = 0L;
        while (split < reference.length() && Character.isLetter(reference.charAt(split))) {
            char letter = Character.toUpperCase(reference.charAt(split));
            column = column * 26L + (letter - 'A' + 1L);
            if (column > Integer.MAX_VALUE) throw tooLarge("Cell column index is too large");
            split++;
        }
        if (split == 0 || split == reference.length()) {
            throw corrupted("Cell reference is invalid", null);
        }
        Integer row = parseInteger(reference.substring(split));
        if (row == null || row <= 0) throw corrupted("Cell row index is invalid", null);
        return new CellPosition(row - 1, (int) column - 1);
    }

    private SpreadsheetMergedRange parseMergedRange(String reference) {
        String[] endpoints = reference.split(":", -1);
        if (endpoints.length != 2) return null;
        try {
            CellPosition start = parseCellPosition(endpoints[0], -1, -1);
            CellPosition end = parseCellPosition(endpoints[1], -1, -1);
            if (start.row >= DocumentLimits.MAX_SPREADSHEET_ROWS
                    || end.row >= DocumentLimits.MAX_SPREADSHEET_ROWS
                    || start.column >= DocumentLimits.MAX_SPREADSHEET_COLUMNS
                    || end.column >= DocumentLimits.MAX_SPREADSHEET_COLUMNS) {
                return null;
            }
            return new SpreadsheetMergedRange(
                    start.row,
                    end.row,
                    start.column,
                    end.column
            );
        } catch (XlsxParseException ignored) {
            return null;
        }
    }

    private CellPosition parseRangeEnd(String reference) {
        if (reference == null || reference.isEmpty()) return null;
        String[] endpoints = reference.split(":", -1);
        String endpoint = endpoints[endpoints.length - 1];
        try {
            return parseCellPosition(endpoint.replace("$", ""), -1, -1);
        } catch (XlsxParseException ignored) {
            return null;
        }
    }

    private void putCell(List<List<String>> rows, int rowIndex, int columnIndex, String value) {
        while (rows.size() <= rowIndex) rows.add(new ArrayList<>());
        List<String> row = rows.get(rowIndex);
        while (row.size() <= columnIndex) row.add("");
        row.set(columnIndex, value);
    }

    private void applyMergedRange(List<List<String>> rows, String reference) {
        String[] endpoints = reference.split(":", -1);
        if (endpoints.length != 2) return;
        try {
            CellPosition start = parseCellPosition(endpoints[0], -1, -1);
            CellPosition end = parseCellPosition(endpoints[1], -1, -1);
            int firstRow = Math.min(start.row, end.row);
            int lastRow = Math.min(DocumentLimits.MAX_SPREADSHEET_ROWS - 1,
                    Math.max(start.row, end.row));
            int firstColumn = Math.min(start.column, end.column);
            int lastColumn = Math.min(DocumentLimits.MAX_SPREADSHEET_COLUMNS - 1,
                    Math.max(start.column, end.column));
            for (int rowIndex = firstRow; rowIndex <= lastRow && rowIndex < rows.size(); rowIndex++) {
                List<String> row = rows.get(rowIndex);
                for (int column = firstColumn; column <= lastColumn && column < row.size(); column++) {
                    if (rowIndex != start.row || column != start.column) row.set(column, "");
                }
            }
        } catch (XlsxParseException ignored) {
            // A malformed merge range does not make otherwise readable cells unsafe.
        }
    }

    private String calculatedRange(List<List<String>> rows) {
        int columns = 0;
        for (List<String> row : rows) columns = Math.max(columns, row.size());
        if (rows.isEmpty() || columns == 0) return "";
        return "A1:" + ColumnLabelFormatter.format(columns - 1) + rows.size();
    }

    private ZipEntry optionalRelatedEntry(
            Map<String, ZipEntry> entries,
            Relationships relationships,
            String typeSuffix,
            String conventionalPath
    ) throws XlsxParseException {
        for (Relationship relationship : relationships.all) {
            if (!relationship.type.endsWith(typeSuffix)) continue;
            if (relationship.external) throw unsupported("External workbook parts are not supported");
            ZipEntry entry = entries.get(relationship.target);
            if (entry == null) throw corrupted("Related workbook part is missing", null);
            return entry;
        }
        return entries.get(conventionalPath);
    }

    private ZipEntry requiredEntry(Map<String, ZipEntry> entries, String name)
            throws XlsxParseException {
        ZipEntry entry = entries.get(name);
        if (entry == null) throw corrupted("Required XLSX part is missing: " + name, null);
        return entry;
    }

    private boolean appendBounded(StringBuilder builder, String value) {
        int remaining = DocumentLimits.MAX_CELL_CHARS - builder.length();
        if (remaining > 0) builder.append(value, 0, Math.min(remaining, value.length()));
        return value.length() > Math.max(0, remaining);
    }

    private String limit(String value) {
        return limit(value, DocumentLimits.MAX_CELL_CHARS);
    }

    private static String limit(String value, int maximum) {
        if (value == null || maximum <= 0) return "";
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isEmpty()) return null;
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private int parseStyleIndex(String value) {
        Integer parsed = parseInteger(value);
        return parsed == null ? -1 : parsed;
    }

    private float parsePositiveFloat(String value, float fallback) {
        if (value == null || value.isEmpty()) return fallback;
        try {
            float parsed = Float.parseFloat(value);
            return Float.isFinite(parsed) && parsed > 0f ? parsed : fallback;
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private boolean parseBoolean(String value) {
        return "1".equals(value) || "true".equalsIgnoreCase(value);
    }

    private static XlsxParseException tooLarge(String message) {
        return new XlsxParseException(XlsxParseException.Reason.TOO_LARGE, message);
    }

    private static XlsxParseException unsupported(String message) {
        return new XlsxParseException(XlsxParseException.Reason.UNSUPPORTED, message);
    }

    private static XlsxParseException corrupted(String message, Throwable cause) {
        return cause == null
                ? new XlsxParseException(XlsxParseException.Reason.CORRUPTED, message)
                : new XlsxParseException(XlsxParseException.Reason.CORRUPTED, message, cause);
    }

    private static final class WorkbookMetadata {
        private final List<SheetReference> sheets;
        private final boolean date1904;
        private final boolean truncated;

        private WorkbookMetadata(
                List<SheetReference> sheets,
                boolean date1904,
                boolean truncated
        ) {
            this.sheets = sheets;
            this.date1904 = date1904;
            this.truncated = truncated;
        }
    }

    private static final class SheetReference {
        private final String name;
        private final String relationshipId;

        private SheetReference(String name, String relationshipId) {
            this.name = name;
            this.relationshipId = relationshipId;
        }
    }

    private static final class Relationships {
        private final Map<String, Relationship> byId;
        private final List<Relationship> all;

        private Relationships(Map<String, Relationship> byId, List<Relationship> all) {
            this.byId = byId;
            this.all = all;
        }
    }

    private static final class Relationship {
        private final String type;
        private final String target;
        private final boolean external;

        private Relationship(String type, String target, boolean external) {
            this.type = type;
            this.target = target;
            this.external = external;
        }
    }

    private static final class SharedStrings {
        private final List<String> values;
        private final boolean truncated;

        private SharedStrings(List<String> values, boolean truncated) {
            this.values = values;
            this.truncated = truncated;
        }
    }

    private static final class Styles {
        private final List<ResolvedStyle> styles;

        private Styles(List<ResolvedStyle> styles) {
            this.styles = styles;
        }

        private static Styles empty() {
            return new Styles(Collections.emptyList());
        }

        private DateStyle getDateStyle(int index) {
            return index >= 0 && index < styles.size()
                    ? styles.get(index).dateStyle
                    : DateStyle.NONE;
        }

        private SpreadsheetCellStyle getVisual(int index) {
            if (index >= 0 && index < styles.size()) return styles.get(index).visualStyle;
            return styles.isEmpty()
                    ? SpreadsheetCellStyle.DEFAULT
                    : styles.get(0).visualStyle;
        }
    }

    private static final class ResolvedStyle {
        private final DateStyle dateStyle;
        private final SpreadsheetCellStyle visualStyle;

        private ResolvedStyle(DateStyle dateStyle, SpreadsheetCellStyle visualStyle) {
            this.dateStyle = dateStyle;
            this.visualStyle = visualStyle;
        }
    }

    private static final class FontStyle {
        private static final FontStyle DEFAULT =
                new FontStyle(false, false, false, 0f, null);

        private final boolean bold;
        private final boolean italic;
        private final boolean underline;
        private final float sizePoints;
        private final Integer color;

        private FontStyle(
                boolean bold,
                boolean italic,
                boolean underline,
                float sizePoints,
                Integer color
        ) {
            this.bold = bold;
            this.italic = italic;
            this.underline = underline;
            this.sizePoints = sizePoints;
            this.color = color;
        }
    }

    private static final class BorderStyle {
        private static final BorderStyle NONE = new BorderStyle(
                SpreadsheetBorder.NONE,
                SpreadsheetBorder.NONE,
                SpreadsheetBorder.NONE,
                SpreadsheetBorder.NONE
        );

        private final SpreadsheetBorder left;
        private final SpreadsheetBorder top;
        private final SpreadsheetBorder right;
        private final SpreadsheetBorder bottom;

        private BorderStyle(
                SpreadsheetBorder left,
                SpreadsheetBorder top,
                SpreadsheetBorder right,
                SpreadsheetBorder bottom
        ) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }
    }

    private static final class CellFormat {
        private final int numberFormatId;
        private final int fontId;
        private final int fillId;
        private final int borderId;
        private final SpreadsheetCellStyle.HorizontalAlignment horizontalAlignment;
        private final SpreadsheetCellStyle.VerticalAlignment verticalAlignment;
        private final boolean wrapText;

        private CellFormat(
                int numberFormatId,
                int fontId,
                int fillId,
                int borderId,
                SpreadsheetCellStyle.HorizontalAlignment horizontalAlignment,
                SpreadsheetCellStyle.VerticalAlignment verticalAlignment,
                boolean wrapText
        ) {
            this.numberFormatId = numberFormatId;
            this.fontId = fontId;
            this.fillId = fillId;
            this.borderId = borderId;
            this.horizontalAlignment = horizontalAlignment;
            this.verticalAlignment = verticalAlignment;
            this.wrapText = wrapText;
        }
    }

    private static final class DateStyle {
        private static final DateStyle NONE = new DateStyle(false, false, false);
        private final boolean date;
        private final boolean hasDate;
        private final boolean hasTime;

        private DateStyle(boolean date, boolean hasDate, boolean hasTime) {
            this.date = date;
            this.hasDate = hasDate;
            this.hasTime = hasTime;
        }
    }

    private static final class RawCell {
        private final String type;
        private final int styleIndex;
        private final String value;
        private final String formula;
        private final String inlineValue;
        private final boolean truncated;

        private RawCell(
                String type,
                int styleIndex,
                String value,
                String formula,
                String inlineValue,
                boolean truncated
        ) {
            this.type = type;
            this.styleIndex = styleIndex;
            this.value = value;
            this.formula = formula;
            this.inlineValue = inlineValue;
            this.truncated = truncated;
        }
    }

    private static final class CellDisplay {
        private final String value;
        private final boolean truncated;

        private CellDisplay(String value, boolean truncated) {
            this.value = value;
            this.truncated = truncated;
        }
    }

    private static final class CellPosition {
        private final int row;
        private final int column;

        private CellPosition(int row, int column) {
            this.row = row;
            this.column = column;
        }
    }

    private static final class RichText {
        private final String value;
        private final boolean truncated;

        private RichText(String value, boolean truncated) {
            this.value = value;
            this.truncated = truncated;
        }
    }

    private static final class SheetParseResult {
        private final XlsxSheet sheet;
        private final boolean truncated;

        private SheetParseResult(XlsxSheet sheet, boolean truncated) {
            this.sheet = sheet;
            this.truncated = truncated;
        }
    }

    private static final class CellCounter {
        private int count;
    }
}
