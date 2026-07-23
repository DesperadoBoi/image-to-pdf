package com.desperadoboi.imagetopdf.document.word;

import com.desperadoboi.imagetopdf.document.DocumentLimits;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class DocxDocumentParser implements WordDocumentParser {
    @Override
    public WordDocumentModel parse(File file, AtomicBoolean cancelled) throws IOException {
        DocxPackageInspector.Inspection inspection =
                DocxPackageInspector.inspect(file, cancelled);
        if (!inspection.isDocx()) {
            throw corrupted("The ZIP package is not a DOCX document", null);
        }
        try (ZipFile zipFile = new ZipFile(file)) {
            Map<String, ZipEntry> entries = entriesByName(zipFile);
            Relationships relationships = readRelationships(
                    zipFile,
                    entries.get(inspection.getMainRelationshipsPart()),
                    inspection.getMainDocumentPart(),
                    cancelled
            );
            Theme theme = readTheme(
                    zipFile,
                    relatedEntry(entries, relationships, "/theme"),
                    cancelled
            );
            StyleSheet styles = readStyles(
                    zipFile,
                    relatedEntry(entries, relationships, "/styles"),
                    theme,
                    cancelled
            );
            Numbering numbering = readNumbering(
                    zipFile,
                    relatedEntry(entries, relationships, "/numbering"),
                    cancelled
            );
            validateOptionalXml(
                    zipFile,
                    relatedEntry(entries, relationships, "/settings"),
                    cancelled
            );

            ParseCounters counters = new ParseCounters();
            PartResult body = readMainDocument(
                    zipFile,
                    requiredEntry(entries, inspection.getMainDocumentPart()),
                    relationships,
                    styles,
                    numbering,
                    counters,
                    cancelled
            );

            List<WordBlock> result = new ArrayList<>();
            Set<String> parsedStories = new HashSet<>();
            appendRelatedStories(
                    result,
                    zipFile,
                    entries,
                    relationships,
                    "/header",
                    WordParagraph.Role.HEADER,
                    styles,
                    numbering,
                    counters,
                    cancelled,
                    parsedStories
            );
            result.addAll(body.blocks);
            appendRelatedStories(
                    result,
                    zipFile,
                    entries,
                    relationships,
                    "/footer",
                    WordParagraph.Role.FOOTER,
                    styles,
                    numbering,
                    counters,
                    cancelled,
                    parsedStories
            );
            appendNotes(
                    result,
                    zipFile,
                    entries,
                    relationships,
                    "/footnotes",
                    "footnote",
                    WordParagraph.Role.FOOTNOTE,
                    styles,
                    numbering,
                    counters,
                    cancelled
            );
            appendNotes(
                    result,
                    zipFile,
                    entries,
                    relationships,
                    "/endnotes",
                    "endnote",
                    WordParagraph.Role.ENDNOTE,
                    styles,
                    numbering,
                    counters,
                    cancelled
            );
            if (result.isEmpty()) {
                throw corrupted("DOCX main document does not contain readable content", null);
            }
            return new WordDocumentModel(
                    result,
                    body.sections,
                    counters.paragraphs,
                    counters.tables,
                    counters.images
            );
        } catch (WordParseException exception) {
            throw exception;
        } catch (XmlPullParserException | RuntimeException exception) {
            throw corrupted("Unable to parse DOCX document", exception);
        } catch (OutOfMemoryError error) {
            throw tooLarge("Not enough memory to parse DOCX", error);
        }
    }

    private Map<String, ZipEntry> entriesByName(ZipFile zipFile) {
        Map<String, ZipEntry> result = new HashMap<>();
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();
            if (entry.isDirectory() && name.endsWith("/")) {
                name = name.substring(0, name.length() - 1) + "/";
            }
            result.put(name, entry);
        }
        return result;
    }

    private Relationships readRelationships(
            ZipFile zipFile,
            ZipEntry entry,
            String sourcePart,
            AtomicBoolean cancelled
    ) throws IOException, XmlPullParserException, WordParseException {
        if (entry == null) return Relationships.empty();
        Map<String, Relationship> byId = new LinkedHashMap<>();
        List<Relationship> all = new ArrayList<>();
        try (InputStream inputStream = zipFile.getInputStream(entry)) {
            XmlPullParser parser = WordXml.newParser(inputStream);
            WordXml.Budget budget = new WordXml.Budget(cancelled);
            int event;
            while ((event = WordXml.next(parser, budget)) != XmlPullParser.END_DOCUMENT) {
                if (event != XmlPullParser.START_TAG
                        || !"Relationship".equals(parser.getName())) {
                    continue;
                }
                String id = WordXml.attribute(parser, "Id");
                String type = WordXml.attribute(parser, "Type");
                boolean external = "External".equalsIgnoreCase(
                        WordXml.attribute(parser, "TargetMode")
                );
                String rawTarget = WordXml.attribute(parser, "Target");
                String target = external
                        ? rawTarget
                        : DocxPackageInspector.normalizeRelationshipTarget(
                                sourcePart,
                                rawTarget
                        );
                if (id == null || type == null || target == null) {
                    throw corrupted("DOCX relationship is invalid", null);
                }
                Relationship relationship =
                        new Relationship(id, type, target, external);
                if (byId.put(id, relationship) != null) {
                    throw corrupted("Duplicate DOCX relationship", null);
                }
                all.add(relationship);
            }
        }
        return new Relationships(byId, all);
    }

    private Theme readTheme(
            ZipFile zipFile,
            ZipEntry entry,
            AtomicBoolean cancelled
    ) throws IOException, XmlPullParserException, WordParseException {
        if (entry == null) return Theme.DEFAULT;
        String majorFont = null;
        String minorFont = null;
        String fontSection = "";
        int sectionDepth = -1;
        Map<String, Integer> colors = new HashMap<>();
        String colorSlot = null;
        int colorDepth = -1;
        try (InputStream inputStream = zipFile.getInputStream(entry)) {
            XmlPullParser parser = WordXml.newParser(inputStream);
            WordXml.Budget budget = new WordXml.Budget(cancelled);
            int event;
            while ((event = WordXml.next(parser, budget)) != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    String name = parser.getName();
                    if ("majorFont".equals(name) || "minorFont".equals(name)) {
                        fontSection = name;
                        sectionDepth = parser.getDepth();
                    } else if ("latin".equals(name) && !fontSection.isEmpty()) {
                        String typeface = WordXml.attribute(parser, "typeface");
                        if ("majorFont".equals(fontSection)) majorFont = typeface;
                        else minorFont = typeface;
                    } else if (isThemeColorSlot(name)) {
                        colorSlot = name;
                        colorDepth = parser.getDepth();
                    } else if (colorSlot != null
                            && ("srgbClr".equals(name) || "sysClr".equals(name))) {
                        String value = "sysClr".equals(name)
                                ? WordXml.attribute(parser, "lastClr")
                                : WordXml.attribute(parser, "val");
                        Integer color = parseHexColor(value);
                        if (color != null) colors.put(colorSlot, color);
                    }
                } else if (event == XmlPullParser.END_TAG) {
                    if (!fontSection.isEmpty() && parser.getDepth() == sectionDepth
                            && fontSection.equals(parser.getName())) {
                        fontSection = "";
                    }
                    if (colorSlot != null && parser.getDepth() == colorDepth
                            && colorSlot.equals(parser.getName())) {
                        colorSlot = null;
                    }
                }
            }
        }
        return new Theme(majorFont, minorFont, colors);
    }

    private StyleSheet readStyles(
            ZipFile zipFile,
            ZipEntry entry,
            Theme theme,
            AtomicBoolean cancelled
    ) throws IOException, XmlPullParserException, WordParseException {
        if (entry == null) {
            return new StyleSheet(
                    WordRunStyle.defaults(),
                    WordParagraphStyle.defaults(),
                    Collections.emptyMap(),
                    theme
            );
        }
        WordRunStyle documentRun = WordRunStyle.defaults();
        WordParagraphStyle documentParagraph = WordParagraphStyle.defaults();
        Map<String, StyleDefinition> definitions = new HashMap<>();
        try (InputStream inputStream = zipFile.getInputStream(entry)) {
            XmlPullParser parser = WordXml.newParser(inputStream);
            WordXml.Budget budget = new WordXml.Budget(cancelled);
            int event;
            while ((event = WordXml.next(parser, budget)) != XmlPullParser.END_DOCUMENT) {
                if (event != XmlPullParser.START_TAG) continue;
                if ("docDefaults".equals(parser.getName())) {
                    DocumentDefaults defaults =
                            readDocumentDefaults(parser, budget, theme);
                    documentRun = WordRunStyle.merge(documentRun, defaults.runStyle);
                    documentParagraph = WordParagraphStyle.merge(
                            documentParagraph,
                            defaults.paragraphStyle
                    );
                } else if ("style".equals(parser.getName())) {
                    StyleDefinition definition = readStyle(parser, budget, theme);
                    if (definition.id != null) {
                        if (definitions.put(definition.id, definition) != null) {
                            throw corrupted("Duplicate Word style identifier", null);
                        }
                    }
                }
            }
        }
        return new StyleSheet(documentRun, documentParagraph, definitions, theme);
    }

    private DocumentDefaults readDocumentDefaults(
            XmlPullParser parser,
            WordXml.Budget budget,
            Theme theme
    ) throws IOException, XmlPullParserException, WordParseException {
        int depth = parser.getDepth();
        WordRunStyle run = null;
        WordParagraphStyle paragraph = null;
        int event;
        while ((event = WordXml.next(parser, budget)) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && "rPr".equals(parser.getName())) {
                run = readRunProperties(parser, budget, theme).style;
            } else if (event == XmlPullParser.START_TAG && "pPr".equals(parser.getName())) {
                ParagraphProperties properties =
                        readParagraphProperties(parser, budget, theme);
                paragraph = properties.style;
                run = WordRunStyle.merge(run, properties.runStyle);
            } else if (event == XmlPullParser.END_TAG
                    && parser.getDepth() == depth
                    && "docDefaults".equals(parser.getName())) {
                break;
            }
        }
        return new DocumentDefaults(run, paragraph);
    }

    private StyleDefinition readStyle(
            XmlPullParser parser,
            WordXml.Budget budget,
            Theme theme
    ) throws IOException, XmlPullParserException, WordParseException {
        int depth = parser.getDepth();
        String id = WordXml.attribute(parser, "styleId");
        String type = WordXml.attribute(parser, "type");
        String basedOn = null;
        String name = null;
        WordRunStyle runStyle = null;
        WordParagraphStyle paragraphStyle = null;
        WordRunStyle paragraphRunStyle = null;
        int event;
        while ((event = WordXml.next(parser, budget)) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                String element = parser.getName();
                if ("basedOn".equals(element)) {
                    basedOn = WordXml.attribute(parser, "val");
                } else if ("name".equals(element)) {
                    name = WordXml.attribute(parser, "val");
                } else if ("pPr".equals(element)) {
                    ParagraphProperties properties =
                            readParagraphProperties(parser, budget, theme);
                    paragraphStyle = properties.style;
                    paragraphRunStyle = properties.runStyle;
                } else if ("rPr".equals(element)) {
                    runStyle = readRunProperties(parser, budget, theme).style;
                }
            } else if (event == XmlPullParser.END_TAG
                    && parser.getDepth() == depth
                    && "style".equals(parser.getName())) {
                break;
            }
        }
        if (paragraphRunStyle != null) {
            runStyle = runStyle == null
                    ? paragraphRunStyle
                    : WordRunStyle.merge(paragraphRunStyle, runStyle);
        }
        return new StyleDefinition(id, type, basedOn, name, paragraphStyle, runStyle);
    }

    private Numbering readNumbering(
            ZipFile zipFile,
            ZipEntry entry,
            AtomicBoolean cancelled
    ) throws IOException, XmlPullParserException, WordParseException {
        if (entry == null) return Numbering.empty();
        Map<Integer, AbstractNumbering> abstractNumbers = new HashMap<>();
        Map<Integer, ConcreteNumbering> concreteNumbers = new HashMap<>();
        try (InputStream inputStream = zipFile.getInputStream(entry)) {
            XmlPullParser parser = WordXml.newParser(inputStream);
            WordXml.Budget budget = new WordXml.Budget(cancelled);
            int event;
            while ((event = WordXml.next(parser, budget)) != XmlPullParser.END_DOCUMENT) {
                if (event != XmlPullParser.START_TAG) continue;
                if ("abstractNum".equals(parser.getName())) {
                    AbstractNumbering abstractNumber =
                            readAbstractNumbering(parser, budget);
                    abstractNumbers.put(abstractNumber.id, abstractNumber);
                } else if ("num".equals(parser.getName())) {
                    ConcreteNumbering concrete = readConcreteNumbering(parser, budget);
                    concreteNumbers.put(concrete.id, concrete);
                }
            }
        }
        return new Numbering(abstractNumbers, concreteNumbers);
    }

    private AbstractNumbering readAbstractNumbering(
            XmlPullParser parser,
            WordXml.Budget budget
    ) throws IOException, XmlPullParserException, WordParseException {
        int id = integer(WordXml.attribute(parser, "abstractNumId"), -1);
        if (id < 0) throw corrupted("Abstract numbering identifier is invalid", null);
        int depth = parser.getDepth();
        Map<Integer, NumberingLevel> levels = new HashMap<>();
        int event;
        while ((event = WordXml.next(parser, budget)) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && "lvl".equals(parser.getName())) {
                NumberingLevel level = readNumberingLevel(parser, budget);
                levels.put(level.level, level);
            } else if (event == XmlPullParser.END_TAG
                    && parser.getDepth() == depth
                    && "abstractNum".equals(parser.getName())) {
                break;
            }
        }
        return new AbstractNumbering(id, levels);
    }

    private NumberingLevel readNumberingLevel(
            XmlPullParser parser,
            WordXml.Budget budget
    ) throws IOException, XmlPullParserException, WordParseException {
        int level = clamp(integer(WordXml.attribute(parser, "ilvl"), 0), 0, 8);
        int depth = parser.getDepth();
        int start = 1;
        String format = "decimal";
        String text = "%" + (level + 1) + ".";
        WordParagraphStyle paragraphStyle = null;
        int event;
        while ((event = WordXml.next(parser, budget)) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                String element = parser.getName();
                if ("start".equals(element)) {
                    start = Math.max(1, integer(WordXml.attribute(parser, "val"), 1));
                } else if ("numFmt".equals(element)) {
                    format = safeValue(WordXml.attribute(parser, "val"), "bullet");
                } else if ("lvlText".equals(element)) {
                    text = safeValue(WordXml.attribute(parser, "val"), "\u2022");
                } else if ("pPr".equals(element)) {
                    paragraphStyle =
                            readParagraphProperties(parser, budget, Theme.DEFAULT).style;
                }
            } else if (event == XmlPullParser.END_TAG
                    && parser.getDepth() == depth
                    && "lvl".equals(parser.getName())) {
                break;
            }
        }
        return new NumberingLevel(level, start, format, text, paragraphStyle);
    }

    private ConcreteNumbering readConcreteNumbering(
            XmlPullParser parser,
            WordXml.Budget budget
    ) throws IOException, XmlPullParserException, WordParseException {
        int id = integer(WordXml.attribute(parser, "numId"), -1);
        if (id < 0) throw corrupted("Numbering identifier is invalid", null);
        int depth = parser.getDepth();
        int abstractId = -1;
        Map<Integer, Integer> startOverrides = new HashMap<>();
        int overrideLevel = -1;
        int overrideDepth = -1;
        int event;
        while ((event = WordXml.next(parser, budget)) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                if ("abstractNumId".equals(parser.getName())) {
                    abstractId = integer(WordXml.attribute(parser, "val"), -1);
                } else if ("lvlOverride".equals(parser.getName())) {
                    overrideLevel = clamp(
                            integer(WordXml.attribute(parser, "ilvl"), 0),
                            0,
                            8
                    );
                    overrideDepth = parser.getDepth();
                } else if ("startOverride".equals(parser.getName())
                        && overrideLevel >= 0) {
                    startOverrides.put(
                            overrideLevel,
                            Math.max(1, integer(WordXml.attribute(parser, "val"), 1))
                    );
                } else if ("lvl".equals(parser.getName()) && overrideLevel >= 0) {
                    NumberingLevel override = readNumberingLevel(parser, budget);
                    startOverrides.put(overrideLevel, override.start);
                }
            } else if (event == XmlPullParser.END_TAG) {
                if (overrideLevel >= 0 && parser.getDepth() == overrideDepth
                        && "lvlOverride".equals(parser.getName())) {
                    overrideLevel = -1;
                }
                if (parser.getDepth() == depth && "num".equals(parser.getName())) break;
            }
        }
        if (abstractId < 0) {
            throw corrupted("Concrete numbering has no abstract definition", null);
        }
        return new ConcreteNumbering(id, abstractId, startOverrides);
    }

    private PartResult readMainDocument(
            ZipFile zipFile,
            ZipEntry entry,
            Relationships relationships,
            StyleSheet styles,
            Numbering numbering,
            ParseCounters counters,
            AtomicBoolean cancelled
    ) throws IOException, XmlPullParserException, WordParseException {
        List<WordBlock> blocks = new ArrayList<>();
        List<WordSectionProperties> sections = new ArrayList<>();
        boolean foundBody = false;
        try (InputStream inputStream = zipFile.getInputStream(entry)) {
            XmlPullParser parser = WordXml.newParser(inputStream);
            WordXml.Budget budget = new WordXml.Budget(cancelled);
            int bodyDepth = -1;
            int event;
            while ((event = WordXml.next(parser, budget)) != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && "body".equals(parser.getName())) {
                    foundBody = true;
                    bodyDepth = parser.getDepth();
                } else if (event == XmlPullParser.START_TAG && bodyDepth >= 0
                        && parser.getDepth() == bodyDepth + 1) {
                    if ("p".equals(parser.getName())) {
                        appendParagraph(
                                blocks,
                                readParagraph(
                                        parser,
                                        budget,
                                        relationships,
                                        styles,
                                        numbering,
                                        WordParagraph.Role.BODY,
                                        counters
                                ),
                                counters
                        );
                    } else if ("tbl".equals(parser.getName())) {
                        blocks.add(readTable(
                                parser,
                                budget,
                                relationships,
                                styles,
                                numbering,
                                WordParagraph.Role.BODY,
                                counters,
                                0
                        ));
                    } else if ("sectPr".equals(parser.getName())) {
                        sections.add(readSectionProperties(parser, budget));
                    }
                } else if (event == XmlPullParser.END_TAG
                        && bodyDepth >= 0
                        && parser.getDepth() == bodyDepth
                        && "body".equals(parser.getName())) {
                    bodyDepth = -1;
                }
            }
        }
        if (!foundBody) throw corrupted("DOCX body is missing", null);
        return new PartResult(blocks, sections);
    }

    private void appendRelatedStories(
            List<WordBlock> destination,
            ZipFile zipFile,
            Map<String, ZipEntry> entries,
            Relationships mainRelationships,
            String relationshipSuffix,
            WordParagraph.Role role,
            StyleSheet styles,
            Numbering numbering,
            ParseCounters counters,
            AtomicBoolean cancelled,
            Set<String> parsedParts
    ) throws IOException, XmlPullParserException, WordParseException {
        for (Relationship relationship : mainRelationships.all) {
            if (!relationship.type.endsWith(relationshipSuffix)
                    || relationship.external
                    || !parsedParts.add(relationship.target)) {
                continue;
            }
            ZipEntry storyEntry = entries.get(relationship.target);
            if (storyEntry == null) throw corrupted("Word story part is missing", null);
            Relationships storyRelationships = readRelationships(
                    zipFile,
                    entries.get(DocxPackageInspector.relationshipPartName(
                            relationship.target
                    )),
                    relationship.target,
                    cancelled
            );
            destination.addAll(readStoryPart(
                    zipFile,
                    storyEntry,
                    storyRelationships,
                    styles,
                    numbering,
                    role,
                    counters,
                    cancelled
            ));
        }
    }

    private List<WordBlock> readStoryPart(
            ZipFile zipFile,
            ZipEntry entry,
            Relationships relationships,
            StyleSheet styles,
            Numbering numbering,
            WordParagraph.Role role,
            ParseCounters counters,
            AtomicBoolean cancelled
    ) throws IOException, XmlPullParserException, WordParseException {
        List<WordBlock> blocks = new ArrayList<>();
        try (InputStream inputStream = zipFile.getInputStream(entry)) {
            XmlPullParser parser = WordXml.newParser(inputStream);
            WordXml.Budget budget = new WordXml.Budget(cancelled);
            int rootDepth = -1;
            int event;
            while ((event = WordXml.next(parser, budget)) != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && rootDepth < 0) {
                    rootDepth = parser.getDepth();
                } else if (event == XmlPullParser.START_TAG
                        && parser.getDepth() == rootDepth + 1) {
                    if ("p".equals(parser.getName())) {
                        appendParagraph(
                                blocks,
                                readParagraph(
                                        parser,
                                        budget,
                                        relationships,
                                        styles,
                                        numbering,
                                        role,
                                        counters
                                ),
                                counters
                        );
                    } else if ("tbl".equals(parser.getName())) {
                        blocks.add(readTable(
                                parser,
                                budget,
                                relationships,
                                styles,
                                numbering,
                                role,
                                counters,
                                0
                        ));
                    }
                }
            }
        }
        return blocks;
    }

    private void appendNotes(
            List<WordBlock> destination,
            ZipFile zipFile,
            Map<String, ZipEntry> entries,
            Relationships relationships,
            String relationshipSuffix,
            String noteElement,
            WordParagraph.Role role,
            StyleSheet styles,
            Numbering numbering,
            ParseCounters counters,
            AtomicBoolean cancelled
    ) throws IOException, XmlPullParserException, WordParseException {
        ZipEntry entry = relatedEntry(entries, relationships, relationshipSuffix);
        if (entry == null) return;
        Relationship partRelationship =
                firstRelationship(relationships, relationshipSuffix);
        String partPath = partRelationship == null ? "" : partRelationship.target;
        Relationships noteRelationships = readRelationships(
                zipFile,
                entries.get(DocxPackageInspector.relationshipPartName(partPath)),
                partPath,
                cancelled
        );
        try (InputStream inputStream = zipFile.getInputStream(entry)) {
            XmlPullParser parser = WordXml.newParser(inputStream);
            WordXml.Budget budget = new WordXml.Budget(cancelled);
            int noteDepth = -1;
            int noteId = -1;
            boolean firstParagraph = false;
            int event;
            while ((event = WordXml.next(parser, budget)) != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG
                        && noteElement.equals(parser.getName())) {
                    noteDepth = parser.getDepth();
                    noteId = integer(WordXml.attribute(parser, "id"), -1);
                    firstParagraph = noteId >= 0;
                    if (noteId < 0) {
                        WordXml.skipElement(parser, budget);
                        noteDepth = -1;
                    }
                } else if (event == XmlPullParser.START_TAG
                        && noteDepth >= 0
                        && parser.getDepth() == noteDepth + 1) {
                    if ("p".equals(parser.getName())) {
                        ParagraphResult parsed = readParagraph(
                                parser,
                                budget,
                                noteRelationships,
                                styles,
                                numbering,
                                role,
                                counters
                        );
                        if (firstParagraph) {
                            parsed = parsed.withPrefix(noteId + ". ", styles.documentRun);
                            firstParagraph = false;
                        }
                        appendParagraph(destination, parsed, counters);
                    } else if ("tbl".equals(parser.getName())) {
                        destination.add(readTable(
                                parser,
                                budget,
                                noteRelationships,
                                styles,
                                numbering,
                                role,
                                counters,
                                0
                        ));
                    }
                } else if (event == XmlPullParser.END_TAG
                        && noteDepth >= 0
                        && parser.getDepth() == noteDepth
                        && noteElement.equals(parser.getName())) {
                    noteDepth = -1;
                }
            }
        }
    }

    private ParagraphResult readParagraph(
            XmlPullParser parser,
            WordXml.Budget budget,
            Relationships relationships,
            StyleSheet styles,
            Numbering numbering,
            WordParagraph.Role role,
            ParseCounters counters
    ) throws IOException, XmlPullParserException, WordParseException {
        counters.addParagraph();
        int depth = parser.getDepth();
        ParagraphProperties direct = ParagraphProperties.empty();
        List<RawRun> rawRuns = new ArrayList<>();
        List<WordImage> images = new ArrayList<>();
        boolean pageBreakAfter = false;
        String hyperlink = null;
        int hyperlinkDepth = -1;
        int event;
        while ((event = WordXml.next(parser, budget)) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                String element = parser.getName();
                if ("pPr".equals(element)) {
                    direct = readParagraphProperties(parser, budget, styles.theme);
                } else if ("hyperlink".equals(element)) {
                    hyperlinkDepth = parser.getDepth();
                    hyperlink = resolveHyperlink(
                            relationships,
                            WordXml.attribute(parser, "id")
                    );
                } else if ("del".equals(element) || "moveFrom".equals(element)) {
                    WordXml.skipElement(parser, budget);
                } else if ("r".equals(element)) {
                    RawRun run = readRun(
                            parser,
                            budget,
                            relationships,
                            hyperlink,
                            styles.theme,
                            counters
                    );
                    rawRuns.add(run);
                    images.addAll(run.images);
                    pageBreakAfter |= run.pageBreak;
                }
            } else if (event == XmlPullParser.END_TAG) {
                if (hyperlinkDepth >= 0 && parser.getDepth() == hyperlinkDepth
                        && "hyperlink".equals(parser.getName())) {
                    hyperlink = null;
                    hyperlinkDepth = -1;
                }
                if (parser.getDepth() == depth && "p".equals(parser.getName())) break;
            }
        }

        ResolvedParagraph resolved = styles.resolveParagraph(direct.styleId);
        WordParagraphStyle paragraphStyle = WordParagraphStyle.merge(
                resolved.paragraphStyle,
                direct.style
        );
        String marker = "";
        if (direct.numberId >= 0) {
            Numbering.Marker numbered =
                    numbering.nextMarker(direct.numberId, direct.numberingLevel);
            marker = numbered.text;
            paragraphStyle = WordParagraphStyle.merge(
                    numbered.paragraphStyle,
                    paragraphStyle
            );
        }
        List<WordRun> runs = new ArrayList<>();
        for (RawRun raw : rawRuns) {
            WordRunStyle runStyle = WordRunStyle.merge(
                    resolved.runStyle,
                    styles.resolveCharacter(raw.styleId)
            );
            runStyle = WordRunStyle.merge(runStyle, direct.runStyle);
            runStyle = WordRunStyle.merge(runStyle, raw.style);
            if (runStyle.isHidden() || raw.text.isEmpty()) continue;
            WordRunStyle finalStyle = raw.hyperlink == null
                    ? runStyle
                    : WordRunStyle.merge(runStyle, new WordRunStyle.Builder()
                            .setUnderline(true)
                            .setColor(0xFF1565C0)
                            .build());
            runs.add(new WordRun(raw.text, finalStyle, raw.hyperlink));
        }
        counters.addBlock();
        WordParagraph paragraph =
                new WordParagraph(runs, paragraphStyle, marker, role);
        return new ParagraphResult(
                paragraph,
                images,
                paragraphStyle.isPageBreakBefore(),
                pageBreakAfter
        );
    }

    private RawRun readRun(
            XmlPullParser parser,
            WordXml.Budget budget,
            Relationships relationships,
            String hyperlink,
            Theme theme,
            ParseCounters counters
    ) throws IOException, XmlPullParserException, WordParseException {
        counters.addRun();
        int depth = parser.getDepth();
        StringBuilder text = new StringBuilder();
        RunProperties properties = RunProperties.empty();
        List<WordImage> images = new ArrayList<>();
        boolean pageBreak = false;
        int event;
        while ((event = WordXml.next(parser, budget)) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                String element = parser.getName();
                if ("rPr".equals(element)) {
                    properties = readRunProperties(parser, budget, theme);
                } else if ("t".equals(element)) {
                    appendRunText(
                            text,
                            readTextElement(parser, budget, "t"),
                            counters
                    );
                } else if ("tab".equals(element)) {
                    appendRunText(text, "\t", counters);
                } else if ("br".equals(element)) {
                    if ("page".equals(WordXml.attribute(parser, "type"))) {
                        pageBreak = true;
                    } else {
                        appendRunText(text, "\n", counters);
                    }
                } else if ("cr".equals(element)) {
                    appendRunText(text, "\n", counters);
                } else if ("softHyphen".equals(element)) {
                    appendRunText(text, "\u00AD", counters);
                } else if ("noBreakHyphen".equals(element)) {
                    appendRunText(text, "\u2011", counters);
                } else if ("sym".equals(element)) {
                    String value = WordXml.attribute(parser, "char");
                    try {
                        appendRunText(
                                text,
                                new String(Character.toChars(
                                        Integer.parseInt(value, 16)
                                )),
                                counters
                        );
                    } catch (RuntimeException ignored) {
                        // Invalid symbol metadata is safely omitted.
                    }
                } else if ("drawing".equals(element) || "pict".equals(element)) {
                    WordImage image = readImage(
                            parser,
                            budget,
                            relationships,
                            counters
                    );
                    if (image != null) images.add(image);
                } else if ("instrText".equals(element) || "delText".equals(element)) {
                    WordXml.skipElement(parser, budget);
                }
            } else if (event == XmlPullParser.END_TAG
                    && parser.getDepth() == depth
                    && "r".equals(parser.getName())) {
                break;
            }
        }
        return new RawRun(
                text.toString(),
                properties.style,
                properties.styleId,
                hyperlink,
                images,
                pageBreak
        );
    }

    private WordImage readImage(
            XmlPullParser parser,
            WordXml.Budget budget,
            Relationships relationships,
            ParseCounters counters
    ) throws IOException, XmlPullParserException, WordParseException {
        int depth = parser.getDepth();
        String endElement = parser.getName();
        String relationshipId = null;
        long widthEmu = 0L;
        long heightEmu = 0L;
        String altText = "";
        int event;
        while ((event = WordXml.next(parser, budget)) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                String element = parser.getName();
                if ("blip".equals(element)) {
                    relationshipId = WordXml.attribute(parser, "embed");
                    if (relationshipId == null) {
                        relationshipId = WordXml.attribute(parser, "id");
                    }
                } else if ("imagedata".equals(element)) {
                    relationshipId = WordXml.attribute(parser, "id");
                } else if ("extent".equals(element)) {
                    widthEmu = longValue(WordXml.attribute(parser, "cx"), widthEmu);
                    heightEmu = longValue(WordXml.attribute(parser, "cy"), heightEmu);
                } else if ("docPr".equals(element)) {
                    altText = safeValue(
                            WordXml.attribute(parser, "descr"),
                            WordXml.attribute(parser, "title")
                    );
                }
            } else if (event == XmlPullParser.END_TAG
                    && parser.getDepth() == depth
                    && endElement.equals(parser.getName())) {
                break;
            }
        }
        if (relationshipId == null) return null;
        Relationship relationship = relationships.byId.get(relationshipId);
        if (relationship == null || relationship.external
                || !relationship.type.endsWith("/image")) {
            throw corrupted("DOCX image relationship is invalid", null);
        }
        counters.addImage();
        String lower = relationship.target.toLowerCase(Locale.ROOT);
        boolean vector = lower.endsWith(".svg")
                || lower.endsWith(".emf")
                || lower.endsWith(".wmf");
        return new WordImage(
                relationshipId,
                relationship.target,
                widthEmu,
                heightEmu,
                limit(altText, 512),
                vector
        );
    }

    private WordTable readTable(
            XmlPullParser parser,
            WordXml.Budget budget,
            Relationships relationships,
            StyleSheet styles,
            Numbering numbering,
            WordParagraph.Role role,
            ParseCounters counters,
            int tableDepth
    ) throws IOException, XmlPullParserException, WordParseException {
        if (tableDepth > DocumentLimits.MAX_WORD_TABLE_DEPTH) {
            throw tooLarge("Nested Word table depth limit exceeded", null);
        }
        counters.addTable();
        int depth = parser.getDepth();
        List<Integer> columnWidths = new ArrayList<>();
        List<WordTableRow> rows = new ArrayList<>();
        TableProperties properties = new TableProperties();
        int event;
        while ((event = WordXml.next(parser, budget)) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                String element = parser.getName();
                if ("tblPr".equals(element) && parser.getDepth() == depth + 1) {
                    properties = readTableProperties(parser, budget);
                } else if ("gridCol".equals(element)) {
                    columnWidths.add(Math.max(
                            0,
                            integer(WordXml.attribute(parser, "w"), 0)
                    ));
                } else if ("tr".equals(element) && parser.getDepth() == depth + 1) {
                    rows.add(readTableRow(
                            parser,
                            budget,
                            relationships,
                            styles,
                            numbering,
                            role,
                            counters,
                            tableDepth
                    ));
                }
            } else if (event == XmlPullParser.END_TAG
                    && parser.getDepth() == depth
                    && "tbl".equals(parser.getName())) {
                break;
            }
        }
        return new WordTable(
                rows,
                columnWidths,
                properties.alignment,
                properties.cellMarginTwips,
                properties.left,
                properties.top,
                properties.right,
                properties.bottom,
                properties.insideHorizontal,
                properties.insideVertical
        );
    }

    private WordTableRow readTableRow(
            XmlPullParser parser,
            WordXml.Budget budget,
            Relationships relationships,
            StyleSheet styles,
            Numbering numbering,
            WordParagraph.Role role,
            ParseCounters counters,
            int tableDepth
    ) throws IOException, XmlPullParserException, WordParseException {
        counters.addTableRow();
        int depth = parser.getDepth();
        int heightTwips = 0;
        List<WordTableCell> cells = new ArrayList<>();
        int event;
        while ((event = WordXml.next(parser, budget)) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                if ("trHeight".equals(parser.getName())) {
                    heightTwips = Math.max(
                            heightTwips,
                            integer(WordXml.attribute(parser, "val"), 0)
                    );
                } else if ("tc".equals(parser.getName())
                        && parser.getDepth() == depth + 1) {
                    cells.add(readTableCell(
                            parser,
                            budget,
                            relationships,
                            styles,
                            numbering,
                            role,
                            counters,
                            tableDepth
                    ));
                }
            } else if (event == XmlPullParser.END_TAG
                    && parser.getDepth() == depth
                    && "tr".equals(parser.getName())) {
                break;
            }
        }
        return new WordTableRow(cells, heightTwips);
    }

    private WordTableCell readTableCell(
            XmlPullParser parser,
            WordXml.Budget budget,
            Relationships relationships,
            StyleSheet styles,
            Numbering numbering,
            WordParagraph.Role role,
            ParseCounters counters,
            int tableDepth
    ) throws IOException, XmlPullParserException, WordParseException {
        counters.addTableCell();
        int depth = parser.getDepth();
        CellProperties properties = new CellProperties();
        List<WordBlock> blocks = new ArrayList<>();
        int event;
        while ((event = WordXml.next(parser, budget)) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG
                    && parser.getDepth() == depth + 1) {
                if ("tcPr".equals(parser.getName())) {
                    properties = readCellProperties(parser, budget);
                } else if ("p".equals(parser.getName())) {
                    appendParagraph(
                            blocks,
                            readParagraph(
                                    parser,
                                    budget,
                                    relationships,
                                    styles,
                                    numbering,
                                    role,
                                    counters
                            ),
                            counters
                    );
                } else if ("tbl".equals(parser.getName())) {
                    blocks.add(readTable(
                            parser,
                            budget,
                            relationships,
                            styles,
                            numbering,
                            role,
                            counters,
                            tableDepth + 1
                    ));
                }
            } else if (event == XmlPullParser.END_TAG
                    && parser.getDepth() == depth
                    && "tc".equals(parser.getName())) {
                break;
            }
        }
        if (blocks.isEmpty()) {
            counters.addParagraph();
            counters.addBlock();
            blocks.add(new WordParagraph(
                    Collections.emptyList(),
                    WordParagraphStyle.defaults(),
                    "",
                    role
            ));
        }
        return new WordTableCell(
                blocks,
                properties.widthTwips,
                properties.gridSpan,
                properties.verticalMerge,
                properties.verticalAlignment,
                properties.shading,
                properties.left,
                properties.top,
                properties.right,
                properties.bottom
        );
    }

    private ParagraphProperties readParagraphProperties(
            XmlPullParser parser,
            WordXml.Budget budget,
            Theme theme
    ) throws IOException, XmlPullParserException, WordParseException {
        int depth = parser.getDepth();
        WordParagraphStyle.Builder style = new WordParagraphStyle.Builder();
        WordRunStyle runStyle = null;
        String styleId = null;
        int numberId = -1;
        int numberingLevel = 0;
        int event;
        while ((event = WordXml.next(parser, budget)) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                String element = parser.getName();
                if ("pStyle".equals(element)) {
                    styleId = WordXml.attribute(parser, "val");
                } else if ("jc".equals(element)) {
                    style.setAlignment(alignment(WordXml.attribute(parser, "val")));
                } else if ("ind".equals(element)) {
                    style.setLeftIndentTwips(optionalInteger(parser, "left", "start"));
                    style.setRightIndentTwips(optionalInteger(parser, "right", "end"));
                    style.setFirstLineIndentTwips(optionalInteger(parser, "firstLine"));
                    style.setHangingIndentTwips(optionalInteger(parser, "hanging"));
                } else if ("spacing".equals(element)) {
                    style.setSpaceBeforeTwips(optionalInteger(parser, "before"));
                    style.setSpaceAfterTwips(optionalInteger(parser, "after"));
                    style.setLineSpacingTwips(optionalInteger(parser, "line"));
                } else if ("keepLines".equals(element)) {
                    style.setKeepTogether(enabledProperty(parser));
                } else if ("pageBreakBefore".equals(element)) {
                    style.setPageBreakBefore(enabledProperty(parser));
                } else if ("outlineLvl".equals(element)) {
                    style.setOutlineLevel(optionalInteger(parser, "val"));
                } else if ("numId".equals(element)) {
                    numberId = integer(WordXml.attribute(parser, "val"), -1);
                } else if ("ilvl".equals(element)) {
                    numberingLevel = clamp(
                            integer(WordXml.attribute(parser, "val"), 0),
                            0,
                            8
                    );
                } else if ("rPr".equals(element)) {
                    runStyle = readRunProperties(parser, budget, theme).style;
                }
            } else if (event == XmlPullParser.END_TAG
                    && parser.getDepth() == depth
                    && "pPr".equals(parser.getName())) {
                break;
            }
        }
        int headingLevel = headingLevel(styleId, null);
        if (headingLevel > 0) style.setHeadingLevel(headingLevel);
        return new ParagraphProperties(
                style.build(),
                runStyle,
                styleId,
                numberId,
                numberingLevel
        );
    }

    private RunProperties readRunProperties(
            XmlPullParser parser,
            WordXml.Budget budget,
            Theme theme
    ) throws IOException, XmlPullParserException, WordParseException {
        int depth = parser.getDepth();
        WordRunStyle.Builder style = new WordRunStyle.Builder();
        String styleId = null;
        int event;
        while ((event = WordXml.next(parser, budget)) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                String element = parser.getName();
                if ("rStyle".equals(element)) {
                    styleId = WordXml.attribute(parser, "val");
                } else if ("rFonts".equals(element)) {
                    String family = firstNonEmpty(
                            WordXml.attribute(parser, "ascii"),
                            WordXml.attribute(parser, "hAnsi"),
                            theme.font(WordXml.attribute(parser, "asciiTheme"))
                    );
                    if (family != null) style.setFontFamily(family);
                } else if ("sz".equals(element) || "szCs".equals(element)) {
                    int halfPoints = integer(WordXml.attribute(parser, "val"), -1);
                    if (halfPoints > 0 && halfPoints <= 400) {
                        style.setFontSizePoints(halfPoints / 2f);
                    }
                } else if ("b".equals(element) || "bCs".equals(element)) {
                    style.setBold(enabledProperty(parser));
                } else if ("i".equals(element) || "iCs".equals(element)) {
                    style.setItalic(enabledProperty(parser));
                } else if ("u".equals(element)) {
                    String value = WordXml.attribute(parser, "val");
                    style.setUnderline(value == null || !"none".equals(value));
                } else if ("strike".equals(element) || "dstrike".equals(element)) {
                    style.setStrike(enabledProperty(parser));
                } else if ("color".equals(element)) {
                    Integer color = parseHexColor(WordXml.attribute(parser, "val"));
                    if (color == null) color = theme.color(
                            WordXml.attribute(parser, "themeColor")
                    );
                    style.setColor(color);
                } else if ("highlight".equals(element)) {
                    style.setHighlight(highlightColor(
                            WordXml.attribute(parser, "val")
                    ));
                } else if ("vertAlign".equals(element)) {
                    String value = WordXml.attribute(parser, "val");
                    style.setVerticalPosition("subscript".equals(value)
                            ? WordRunStyle.VerticalPosition.SUBSCRIPT
                            : "superscript".equals(value)
                                    ? WordRunStyle.VerticalPosition.SUPERSCRIPT
                                    : WordRunStyle.VerticalPosition.BASELINE);
                } else if ("vanish".equals(element) || "webHidden".equals(element)) {
                    style.setHidden(enabledProperty(parser));
                }
            } else if (event == XmlPullParser.END_TAG
                    && parser.getDepth() == depth
                    && "rPr".equals(parser.getName())) {
                break;
            }
        }
        return new RunProperties(style.build(), styleId);
    }

    private TableProperties readTableProperties(
            XmlPullParser parser,
            WordXml.Budget budget
    ) throws IOException, XmlPullParserException, WordParseException {
        int depth = parser.getDepth();
        TableProperties result = new TableProperties();
        int event;
        while ((event = WordXml.next(parser, budget)) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                String element = parser.getName();
                if ("jc".equals(element)) {
                    String value = WordXml.attribute(parser, "val");
                    result.alignment = "center".equals(value)
                            ? WordTable.Alignment.CENTER
                            : "right".equals(value) || "end".equals(value)
                                    ? WordTable.Alignment.RIGHT
                                    : WordTable.Alignment.LEFT;
                } else if ("tblCellMar".equals(element)) {
                    result.cellMarginTwips =
                            readCellMargin(parser, budget, result.cellMarginTwips);
                } else if ("tblBorders".equals(element)) {
                    readTableBorders(parser, budget, result);
                }
            } else if (event == XmlPullParser.END_TAG
                    && parser.getDepth() == depth
                    && "tblPr".equals(parser.getName())) {
                break;
            }
        }
        return result;
    }

    private int readCellMargin(
            XmlPullParser parser,
            WordXml.Budget budget,
            int fallback
    ) throws IOException, XmlPullParserException, WordParseException {
        int depth = parser.getDepth();
        int total = 0;
        int values = 0;
        int event;
        while ((event = WordXml.next(parser, budget)) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                String element = parser.getName();
                if ("left".equals(element) || "right".equals(element)
                        || "start".equals(element) || "end".equals(element)) {
                    int value = integer(WordXml.attribute(parser, "w"), -1);
                    if (value >= 0) {
                        total += value;
                        values++;
                    }
                }
            } else if (event == XmlPullParser.END_TAG
                    && parser.getDepth() == depth
                    && "tblCellMar".equals(parser.getName())) {
                break;
            }
        }
        return values == 0 ? fallback : total / values;
    }

    private void readTableBorders(
            XmlPullParser parser,
            WordXml.Budget budget,
            TableProperties result
    ) throws IOException, XmlPullParserException, WordParseException {
        int depth = parser.getDepth();
        int event;
        while ((event = WordXml.next(parser, budget)) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                WordBorder border = border(parser);
                switch (parser.getName()) {
                    case "left":
                    case "start":
                        result.left = border;
                        break;
                    case "top":
                        result.top = border;
                        break;
                    case "right":
                    case "end":
                        result.right = border;
                        break;
                    case "bottom":
                        result.bottom = border;
                        break;
                    case "insideH":
                        result.insideHorizontal = border;
                        break;
                    case "insideV":
                        result.insideVertical = border;
                        break;
                    default:
                        break;
                }
            } else if (event == XmlPullParser.END_TAG
                    && parser.getDepth() == depth
                    && "tblBorders".equals(parser.getName())) {
                break;
            }
        }
    }

    private CellProperties readCellProperties(
            XmlPullParser parser,
            WordXml.Budget budget
    ) throws IOException, XmlPullParserException, WordParseException {
        int depth = parser.getDepth();
        CellProperties result = new CellProperties();
        int borderDepth = -1;
        int event;
        while ((event = WordXml.next(parser, budget)) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                String element = parser.getName();
                if ("tcW".equals(element)) {
                    result.widthTwips = Math.max(
                            0,
                            integer(WordXml.attribute(parser, "w"), 0)
                    );
                } else if ("gridSpan".equals(element)) {
                    result.gridSpan = clamp(
                            integer(WordXml.attribute(parser, "val"), 1),
                            1,
                            256
                    );
                } else if ("vMerge".equals(element)) {
                    String value = WordXml.attribute(parser, "val");
                    result.verticalMerge = "restart".equals(value)
                            ? WordTableCell.VerticalMerge.RESTART
                            : WordTableCell.VerticalMerge.CONTINUE;
                } else if ("vAlign".equals(element)) {
                    String value = WordXml.attribute(parser, "val");
                    result.verticalAlignment = "center".equals(value)
                            ? WordTableCell.VerticalAlignment.CENTER
                            : "bottom".equals(value)
                                    ? WordTableCell.VerticalAlignment.BOTTOM
                                    : WordTableCell.VerticalAlignment.TOP;
                } else if ("shd".equals(element)) {
                    result.shading = parseHexColor(firstNonEmpty(
                            WordXml.attribute(parser, "fill"),
                            WordXml.attribute(parser, "color")
                    ));
                } else if ("tcBorders".equals(element)) {
                    borderDepth = parser.getDepth();
                } else if (borderDepth >= 0 && parser.getDepth() == borderDepth + 1) {
                    WordBorder border = border(parser);
                    if ("left".equals(element) || "start".equals(element)) {
                        result.left = border;
                    } else if ("top".equals(element)) {
                        result.top = border;
                    } else if ("right".equals(element) || "end".equals(element)) {
                        result.right = border;
                    } else if ("bottom".equals(element)) {
                        result.bottom = border;
                    }
                }
            } else if (event == XmlPullParser.END_TAG) {
                if (borderDepth >= 0 && parser.getDepth() == borderDepth
                        && "tcBorders".equals(parser.getName())) {
                    borderDepth = -1;
                }
                if (parser.getDepth() == depth && "tcPr".equals(parser.getName())) break;
            }
        }
        return result;
    }

    private WordSectionProperties readSectionProperties(
            XmlPullParser parser,
            WordXml.Budget budget
    ) throws IOException, XmlPullParserException, WordParseException {
        int depth = parser.getDepth();
        int pageWidth = 0;
        int pageHeight = 0;
        int top = 0;
        int right = 0;
        int bottom = 0;
        int left = 0;
        List<String> headers = new ArrayList<>();
        List<String> footers = new ArrayList<>();
        int event;
        while ((event = WordXml.next(parser, budget)) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                if ("pgSz".equals(parser.getName())) {
                    pageWidth = integer(WordXml.attribute(parser, "w"), 0);
                    pageHeight = integer(WordXml.attribute(parser, "h"), 0);
                } else if ("pgMar".equals(parser.getName())) {
                    top = integer(WordXml.attribute(parser, "top"), 0);
                    right = integer(WordXml.attribute(parser, "right"), 0);
                    bottom = integer(WordXml.attribute(parser, "bottom"), 0);
                    left = integer(WordXml.attribute(parser, "left"), 0);
                } else if ("headerReference".equals(parser.getName())) {
                    addNonEmpty(headers, WordXml.attribute(parser, "id"));
                } else if ("footerReference".equals(parser.getName())) {
                    addNonEmpty(footers, WordXml.attribute(parser, "id"));
                }
            } else if (event == XmlPullParser.END_TAG
                    && parser.getDepth() == depth
                    && "sectPr".equals(parser.getName())) {
                break;
            }
        }
        return new WordSectionProperties(
                pageWidth,
                pageHeight,
                top,
                right,
                bottom,
                left,
                headers,
                footers
        );
    }

    private void validateOptionalXml(
            ZipFile zipFile,
            ZipEntry entry,
            AtomicBoolean cancelled
    ) throws IOException, XmlPullParserException, WordParseException {
        if (entry == null) return;
        try (InputStream inputStream = zipFile.getInputStream(entry)) {
            XmlPullParser parser = WordXml.newParser(inputStream);
            WordXml.Budget budget = new WordXml.Budget(cancelled);
            while (WordXml.next(parser, budget) != XmlPullParser.END_DOCUMENT) {
                // Consuming through the bounded XML reader is the validation.
            }
        }
    }

    private void appendParagraph(
            List<WordBlock> blocks,
            ParagraphResult result,
            ParseCounters counters
    ) throws WordParseException {
        if (result.pageBreakBefore) {
            counters.addBlock();
            blocks.add(new WordPageBreak());
        }
        blocks.add(result.paragraph);
        for (WordImage image : result.images) {
            counters.addBlock();
            blocks.add(image);
        }
        if (result.pageBreakAfter) {
            counters.addBlock();
            blocks.add(new WordPageBreak());
        }
    }

    private String resolveHyperlink(Relationships relationships, String id) {
        if (id == null) return null;
        Relationship relationship = relationships.byId.get(id);
        if (relationship == null || !relationship.external
                || !relationship.type.endsWith("/hyperlink")) {
            return null;
        }
        return relationship.target != null
                && relationship.target.toLowerCase(Locale.ROOT).startsWith("https://")
                ? relationship.target
                : null;
    }

    private ZipEntry relatedEntry(
            Map<String, ZipEntry> entries,
            Relationships relationships,
            String typeSuffix
    ) throws WordParseException {
        Relationship relationship = firstRelationship(relationships, typeSuffix);
        if (relationship == null) return null;
        if (relationship.external) {
            throw unsupported("External DOCX part is not supported");
        }
        ZipEntry entry = entries.get(relationship.target);
        if (entry == null) throw corrupted("Related DOCX part is missing", null);
        return entry;
    }

    private Relationship firstRelationship(Relationships relationships, String typeSuffix) {
        for (Relationship relationship : relationships.all) {
            if (relationship.type.endsWith(typeSuffix)) return relationship;
        }
        return null;
    }

    private ZipEntry requiredEntry(Map<String, ZipEntry> entries, String name)
            throws WordParseException {
        ZipEntry entry = entries.get(name);
        if (entry == null) throw corrupted("Required DOCX part is missing", null);
        return entry;
    }

    private String readTextElement(
            XmlPullParser parser,
            WordXml.Budget budget,
            String endElement
    ) throws IOException, XmlPullParserException, WordParseException {
        int depth = parser.getDepth();
        StringBuilder result = new StringBuilder();
        int event;
        while ((event = WordXml.next(parser, budget)) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.TEXT || event == XmlPullParser.CDSECT
                    || event == XmlPullParser.ENTITY_REF) {
                if (parser.getText() != null) result.append(parser.getText());
            } else if (event == XmlPullParser.END_TAG
                    && parser.getDepth() == depth
                    && endElement.equals(parser.getName())) {
                return result.toString();
            }
        }
        throw corrupted("Word text element is incomplete", null);
    }

    private void appendRunText(
            StringBuilder builder,
            String value,
            ParseCounters counters
    ) throws WordParseException {
        if (value == null || value.isEmpty()) return;
        if (builder.length() + value.length() > DocumentLimits.MAX_WORD_RUN_CHARS) {
            throw tooLarge("Word text run length limit exceeded", null);
        }
        counters.addText(value.length());
        builder.append(value);
    }

    private WordBorder border(XmlPullParser parser) {
        String value = WordXml.attribute(parser, "val");
        WordBorder.Style style;
        if (value == null || "nil".equals(value) || "none".equals(value)) {
            style = WordBorder.Style.NONE;
        } else if ("double".equals(value)) {
            style = WordBorder.Style.DOUBLE;
        } else if (value.contains("dash")) {
            style = WordBorder.Style.DASHED;
        } else if (value.contains("dot")) {
            style = WordBorder.Style.DOTTED;
        } else if ("thick".equals(value) || "triple".equals(value)) {
            style = WordBorder.Style.THICK;
        } else {
            style = WordBorder.Style.SINGLE;
        }
        Integer color = parseHexColor(WordXml.attribute(parser, "color"));
        return new WordBorder(
                style,
                color == null ? 0xFF7A8491 : color,
                integer(WordXml.attribute(parser, "sz"), 4)
        );
    }

    private WordParagraphStyle.Alignment alignment(String value) {
        if ("center".equals(value)) return WordParagraphStyle.Alignment.CENTER;
        if ("right".equals(value) || "end".equals(value)) {
            return WordParagraphStyle.Alignment.RIGHT;
        }
        if ("both".equals(value) || "distribute".equals(value)
                || "thaiDistribute".equals(value)) {
            return WordParagraphStyle.Alignment.JUSTIFY;
        }
        return WordParagraphStyle.Alignment.LEFT;
    }

    private Boolean enabledProperty(XmlPullParser parser) {
        String value = WordXml.attribute(parser, "val");
        return value == null || !("0".equals(value)
                || "false".equalsIgnoreCase(value)
                || "off".equalsIgnoreCase(value));
    }

    private Integer optionalInteger(XmlPullParser parser, String... names) {
        for (String name : names) {
            String value = WordXml.attribute(parser, name);
            if (value != null) {
                try {
                    return Integer.valueOf(value);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private int headingLevel(String styleId, String styleName) {
        String value = ((styleId == null ? "" : styleId) + " "
                + (styleName == null ? "" : styleName)).toLowerCase(Locale.ROOT);
        for (int level = 1; level <= 9; level++) {
            if (value.contains("heading" + level)
                    || value.contains("heading " + level)
                    || value.contains("\u0437\u0430\u0433\u043e\u043b\u043e\u0432\u043e\u043a " + level)) {
                return level;
            }
        }
        return 0;
    }

    private Integer parseHexColor(String value) {
        if (value == null || value.isEmpty() || "auto".equalsIgnoreCase(value)
                || "none".equalsIgnoreCase(value)) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() == 8) normalized = normalized.substring(2);
        if (normalized.length() != 6) return null;
        try {
            return 0xFF000000 | Integer.parseInt(normalized, 16);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Integer highlightColor(String value) {
        if (value == null || "none".equals(value)) return null;
        switch (value) {
            case "yellow": return 0xFFFFFF00;
            case "green": return 0xFF00FF00;
            case "cyan": return 0xFF00FFFF;
            case "magenta": return 0xFFFF00FF;
            case "blue": return 0xFF4F81BD;
            case "red": return 0xFFFF0000;
            case "darkBlue": return 0xFF000080;
            case "darkCyan": return 0xFF008080;
            case "darkGreen": return 0xFF008000;
            case "darkMagenta": return 0xFF800080;
            case "darkRed": return 0xFF800000;
            case "darkYellow": return 0xFF808000;
            case "darkGray": return 0xFF808080;
            case "lightGray": return 0xFFC0C0C0;
            case "black": return 0xFF000000;
            case "white": return 0xFFFFFFFF;
            default: return null;
        }
    }

    private boolean isThemeColorSlot(String value) {
        return "dk1".equals(value) || "lt1".equals(value)
                || "dk2".equals(value) || "lt2".equals(value)
                || value.startsWith("accent") || "hlink".equals(value)
                || "folHlink".equals(value);
    }

    private String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value;
        }
        return null;
    }

    private String safeValue(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }

    private int integer(String value, int fallback) {
        if (value == null || value.isEmpty()) return fallback;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private long longValue(String value, long fallback) {
        if (value == null || value.isEmpty()) return fallback;
        try {
            long result = Long.parseLong(value);
            return result < 0L ? fallback : result;
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private String limit(String value, int maximum) {
        if (value == null) return "";
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }

    private void addNonEmpty(List<String> values, String value) {
        if (value != null && !value.isEmpty() && !values.contains(value)) values.add(value);
    }

    private int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static WordParseException corrupted(String message, Throwable cause) {
        return cause == null
                ? new WordParseException(WordParseException.Reason.CORRUPTED, message)
                : new WordParseException(
                        WordParseException.Reason.CORRUPTED,
                        message,
                        cause
                );
    }

    private static WordParseException tooLarge(String message, Throwable cause) {
        return cause == null
                ? new WordParseException(WordParseException.Reason.TOO_LARGE, message)
                : new WordParseException(
                        WordParseException.Reason.TOO_LARGE,
                        message,
                        cause
                );
    }

    private static WordParseException unsupported(String message) {
        return new WordParseException(WordParseException.Reason.UNSUPPORTED, message);
    }

    private static final class Relationships {
        private final Map<String, Relationship> byId;
        private final List<Relationship> all;

        private Relationships(
                Map<String, Relationship> byId,
                List<Relationship> all
        ) {
            this.byId = byId;
            this.all = all;
        }

        private static Relationships empty() {
            return new Relationships(Collections.emptyMap(), Collections.emptyList());
        }
    }

    private static final class Relationship {
        private final String id;
        private final String type;
        private final String target;
        private final boolean external;

        private Relationship(String id, String type, String target, boolean external) {
            this.id = id;
            this.type = type;
            this.target = target;
            this.external = external;
        }
    }

    private final class StyleSheet {
        private final WordRunStyle documentRun;
        private final WordParagraphStyle documentParagraph;
        private final Map<String, StyleDefinition> definitions;
        private final Map<String, ResolvedParagraph> paragraphCache = new HashMap<>();
        private final Map<String, WordRunStyle> characterCache = new HashMap<>();
        private final Theme theme;

        private StyleSheet(
                WordRunStyle documentRun,
                WordParagraphStyle documentParagraph,
                Map<String, StyleDefinition> definitions
        ) {
            this.documentRun = documentRun;
            this.documentParagraph = documentParagraph;
            this.definitions = definitions;
            theme = Theme.DEFAULT;
        }

        private StyleSheet(
                WordRunStyle documentRun,
                WordParagraphStyle documentParagraph,
                Map<String, StyleDefinition> definitions,
                Theme theme
        ) {
            this.documentRun = documentRun;
            this.documentParagraph = documentParagraph;
            this.definitions = definitions;
            this.theme = theme;
        }

        private ResolvedParagraph resolveParagraph(String id)
                throws WordParseException {
            if (id == null || id.isEmpty()) {
                return new ResolvedParagraph(documentParagraph, documentRun);
            }
            ResolvedParagraph cached = paragraphCache.get(id);
            if (cached != null) return cached;
            ResolvedParagraph resolved = resolveParagraph(
                    id,
                    new HashSet<>(),
                    0
            );
            paragraphCache.put(id, resolved);
            return resolved;
        }

        private ResolvedParagraph resolveParagraph(
                String id,
                Set<String> visiting,
                int depth
        ) throws WordParseException {
            if (depth > DocumentLimits.MAX_WORD_STYLE_DEPTH || !visiting.add(id)) {
                throw corrupted("Cyclic Word paragraph style inheritance", null);
            }
            StyleDefinition definition = definitions.get(id);
            if (definition == null) {
                visiting.remove(id);
                return new ResolvedParagraph(documentParagraph, documentRun);
            }
            ResolvedParagraph base = definition.basedOn == null
                    ? new ResolvedParagraph(documentParagraph, documentRun)
                    : resolveParagraph(definition.basedOn, visiting, depth + 1);
            WordParagraphStyle paragraph = WordParagraphStyle.merge(
                    base.paragraphStyle,
                    definition.paragraphStyle
            );
            int heading = headingLevel(definition.id, definition.name);
            if (heading > 0) {
                paragraph = WordParagraphStyle.merge(
                        paragraph,
                        new WordParagraphStyle.Builder()
                                .setHeadingLevel(heading)
                                .setOutlineLevel(heading - 1)
                                .build()
                );
            }
            WordRunStyle run = WordRunStyle.merge(base.runStyle, definition.runStyle);
            visiting.remove(id);
            return new ResolvedParagraph(paragraph, run);
        }

        private WordRunStyle resolveCharacter(String id) throws WordParseException {
            if (id == null || id.isEmpty()) return null;
            WordRunStyle cached = characterCache.get(id);
            if (cached != null) return cached;
            WordRunStyle result = resolveCharacter(id, new HashSet<>(), 0);
            characterCache.put(id, result);
            return result;
        }

        private WordRunStyle resolveCharacter(
                String id,
                Set<String> visiting,
                int depth
        ) throws WordParseException {
            if (depth > DocumentLimits.MAX_WORD_STYLE_DEPTH || !visiting.add(id)) {
                throw corrupted("Cyclic Word character style inheritance", null);
            }
            StyleDefinition definition = definitions.get(id);
            if (definition == null) {
                visiting.remove(id);
                return null;
            }
            WordRunStyle base = definition.basedOn == null
                    ? null
                    : resolveCharacter(definition.basedOn, visiting, depth + 1);
            WordRunStyle result = WordRunStyle.merge(base, definition.runStyle);
            visiting.remove(id);
            return result;
        }

    }

    private static final class StyleDefinition {
        private final String id;
        private final String type;
        private final String basedOn;
        private final String name;
        private final WordParagraphStyle paragraphStyle;
        private final WordRunStyle runStyle;

        private StyleDefinition(
                String id,
                String type,
                String basedOn,
                String name,
                WordParagraphStyle paragraphStyle,
                WordRunStyle runStyle
        ) {
            this.id = id;
            this.type = type;
            this.basedOn = basedOn;
            this.name = name;
            this.paragraphStyle = paragraphStyle;
            this.runStyle = runStyle;
        }
    }

    private static final class ResolvedParagraph {
        private final WordParagraphStyle paragraphStyle;
        private final WordRunStyle runStyle;

        private ResolvedParagraph(
                WordParagraphStyle paragraphStyle,
                WordRunStyle runStyle
        ) {
            this.paragraphStyle = paragraphStyle;
            this.runStyle = runStyle;
        }
    }

    private static final class DocumentDefaults {
        private final WordRunStyle runStyle;
        private final WordParagraphStyle paragraphStyle;

        private DocumentDefaults(
                WordRunStyle runStyle,
                WordParagraphStyle paragraphStyle
        ) {
            this.runStyle = runStyle;
            this.paragraphStyle = paragraphStyle;
        }
    }

    private static final class ParagraphProperties {
        private final WordParagraphStyle style;
        private final WordRunStyle runStyle;
        private final String styleId;
        private final int numberId;
        private final int numberingLevel;

        private ParagraphProperties(
                WordParagraphStyle style,
                WordRunStyle runStyle,
                String styleId,
                int numberId,
                int numberingLevel
        ) {
            this.style = style;
            this.runStyle = runStyle;
            this.styleId = styleId;
            this.numberId = numberId;
            this.numberingLevel = numberingLevel;
        }

        private static ParagraphProperties empty() {
            return new ParagraphProperties(
                    new WordParagraphStyle.Builder().build(),
                    null,
                    null,
                    -1,
                    0
            );
        }
    }

    private static final class RunProperties {
        private final WordRunStyle style;
        private final String styleId;

        private RunProperties(WordRunStyle style, String styleId) {
            this.style = style;
            this.styleId = styleId;
        }

        private static RunProperties empty() {
            return new RunProperties(null, null);
        }
    }

    private static final class RawRun {
        private final String text;
        private final WordRunStyle style;
        private final String styleId;
        private final String hyperlink;
        private final List<WordImage> images;
        private final boolean pageBreak;

        private RawRun(
                String text,
                WordRunStyle style,
                String styleId,
                String hyperlink,
                List<WordImage> images,
                boolean pageBreak
        ) {
            this.text = text;
            this.style = style;
            this.styleId = styleId;
            this.hyperlink = hyperlink;
            this.images = images;
            this.pageBreak = pageBreak;
        }
    }

    private static final class ParagraphResult {
        private final WordParagraph paragraph;
        private final List<WordImage> images;
        private final boolean pageBreakBefore;
        private final boolean pageBreakAfter;

        private ParagraphResult(
                WordParagraph paragraph,
                List<WordImage> images,
                boolean pageBreakBefore,
                boolean pageBreakAfter
        ) {
            this.paragraph = paragraph;
            this.images = images;
            this.pageBreakBefore = pageBreakBefore;
            this.pageBreakAfter = pageBreakAfter;
        }

        private ParagraphResult withPrefix(String value, WordRunStyle style) {
            List<WordRun> runs = new ArrayList<>();
            runs.add(new WordRun(value, style, null));
            runs.addAll(paragraph.getRuns());
            return new ParagraphResult(
                    new WordParagraph(
                            runs,
                            paragraph.getStyle(),
                            paragraph.getListMarker(),
                            paragraph.getRole()
                    ),
                    images,
                    pageBreakBefore,
                    pageBreakAfter
            );
        }
    }

    private static final class PartResult {
        private final List<WordBlock> blocks;
        private final List<WordSectionProperties> sections;

        private PartResult(
                List<WordBlock> blocks,
                List<WordSectionProperties> sections
        ) {
            this.blocks = blocks;
            this.sections = sections;
        }
    }

    private static final class TableProperties {
        private WordTable.Alignment alignment = WordTable.Alignment.LEFT;
        private int cellMarginTwips = 100;
        private WordBorder left = WordBorder.NONE;
        private WordBorder top = WordBorder.NONE;
        private WordBorder right = WordBorder.NONE;
        private WordBorder bottom = WordBorder.NONE;
        private WordBorder insideHorizontal = WordBorder.NONE;
        private WordBorder insideVertical = WordBorder.NONE;
    }

    private static final class CellProperties {
        private int widthTwips;
        private int gridSpan = 1;
        private WordTableCell.VerticalMerge verticalMerge =
                WordTableCell.VerticalMerge.NONE;
        private WordTableCell.VerticalAlignment verticalAlignment =
                WordTableCell.VerticalAlignment.TOP;
        private Integer shading;
        private WordBorder left = WordBorder.NONE;
        private WordBorder top = WordBorder.NONE;
        private WordBorder right = WordBorder.NONE;
        private WordBorder bottom = WordBorder.NONE;
    }

    private static final class Theme {
        private static final Theme DEFAULT = new Theme(
                "serif",
                "sans-serif",
                Collections.emptyMap()
        );

        private final String majorFont;
        private final String minorFont;
        private final Map<String, Integer> colors;

        private Theme(
                String majorFont,
                String minorFont,
                Map<String, Integer> colors
        ) {
            this.majorFont = majorFont == null || majorFont.isEmpty()
                    ? "serif"
                    : majorFont;
            this.minorFont = minorFont == null || minorFont.isEmpty()
                    ? "sans-serif"
                    : minorFont;
            this.colors = new HashMap<>(colors);
        }

        private String font(String themeName) {
            if (themeName == null) return null;
            return themeName.toLowerCase(Locale.ROOT).contains("major")
                    ? majorFont
                    : minorFont;
        }

        private Integer color(String name) {
            return name == null ? null : colors.get(name);
        }
    }

    private static final class AbstractNumbering {
        private final int id;
        private final Map<Integer, NumberingLevel> levels;

        private AbstractNumbering(int id, Map<Integer, NumberingLevel> levels) {
            this.id = id;
            this.levels = levels;
        }
    }

    private static final class ConcreteNumbering {
        private final int id;
        private final int abstractId;
        private final Map<Integer, Integer> startOverrides;

        private ConcreteNumbering(
                int id,
                int abstractId,
                Map<Integer, Integer> startOverrides
        ) {
            this.id = id;
            this.abstractId = abstractId;
            this.startOverrides = startOverrides;
        }
    }

    private static final class NumberingLevel {
        private final int level;
        private final int start;
        private final String format;
        private final String text;
        private final WordParagraphStyle paragraphStyle;

        private NumberingLevel(
                int level,
                int start,
                String format,
                String text,
                WordParagraphStyle paragraphStyle
        ) {
            this.level = level;
            this.start = start;
            this.format = format;
            this.text = text;
            this.paragraphStyle = paragraphStyle;
        }
    }

    private static final class Numbering {
        private final Map<Integer, AbstractNumbering> abstractNumbers;
        private final Map<Integer, ConcreteNumbering> concreteNumbers;
        private final Map<Integer, int[]> counters = new HashMap<>();
        private final Set<Long> initialized = new HashSet<>();

        private Numbering(
                Map<Integer, AbstractNumbering> abstractNumbers,
                Map<Integer, ConcreteNumbering> concreteNumbers
        ) {
            this.abstractNumbers = abstractNumbers;
            this.concreteNumbers = concreteNumbers;
        }

        private static Numbering empty() {
            return new Numbering(Collections.emptyMap(), Collections.emptyMap());
        }

        private Marker nextMarker(int numberId, int requestedLevel) {
            int level = Math.max(0, Math.min(8, requestedLevel));
            ConcreteNumbering concrete = concreteNumbers.get(numberId);
            AbstractNumbering abstractNumber = concrete == null
                    ? null
                    : abstractNumbers.get(concrete.abstractId);
            NumberingLevel definition = abstractNumber == null
                    ? null
                    : abstractNumber.levels.get(level);
            if (definition == null) {
                return new Marker("\u2022 ", null);
            }
            int[] values = counters.computeIfAbsent(numberId, ignored -> new int[9]);
            long key = (((long) numberId) << 32) | (level & 0xFFFFFFFFL);
            int start = concrete.startOverrides.containsKey(level)
                    ? concrete.startOverrides.get(level)
                    : definition.start;
            if (initialized.add(key)) values[level] = start;
            else values[level]++;
            for (int deeper = level + 1; deeper < values.length; deeper++) {
                values[deeper] = 0;
                initialized.remove((((long) numberId) << 32)
                        | (deeper & 0xFFFFFFFFL));
            }
            String marker = definition.text;
            for (int index = 0; index <= level; index++) {
                NumberingLevel placeholderDefinition =
                        abstractNumber.levels.get(index);
                String formatted = placeholderDefinition == null
                        ? Integer.toString(Math.max(1, values[index]))
                        : formatNumber(
                                Math.max(1, values[index]),
                                placeholderDefinition.format
                        );
                marker = marker.replace("%" + (index + 1), formatted);
            }
            if (!marker.endsWith(" ") && !marker.endsWith("\t")) marker += " ";
            return new Marker(marker, definition.paragraphStyle);
        }

        private static String formatNumber(int value, String format) {
            switch (format) {
                case "lowerLetter": return letters(value).toLowerCase(Locale.ROOT);
                case "upperLetter": return letters(value);
                case "lowerRoman": return roman(value).toLowerCase(Locale.ROOT);
                case "upperRoman": return roman(value);
                case "bullet": return "\u2022";
                case "decimal":
                case "decimalZero":
                    return Integer.toString(value);
                default:
                    return "\u2022";
            }
        }

        private static String letters(int value) {
            StringBuilder result = new StringBuilder();
            int current = Math.max(1, value);
            while (current > 0 && result.length() < 8) {
                current--;
                result.append((char) ('A' + (current % 26)));
                current /= 26;
            }
            return result.reverse().toString();
        }

        private static String roman(int value) {
            if (value <= 0 || value > 3_999) return Integer.toString(value);
            int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
            String[] symbols = {
                    "M", "CM", "D", "CD", "C", "XC", "L",
                    "XL", "X", "IX", "V", "IV", "I"
            };
            StringBuilder result = new StringBuilder();
            int remaining = value;
            for (int index = 0; index < values.length; index++) {
                while (remaining >= values[index]) {
                    result.append(symbols[index]);
                    remaining -= values[index];
                }
            }
            return result.toString();
        }

        private static final class Marker {
            private final String text;
            private final WordParagraphStyle paragraphStyle;

            private Marker(String text, WordParagraphStyle paragraphStyle) {
                this.text = text;
                this.paragraphStyle = paragraphStyle;
            }
        }
    }

    private static final class ParseCounters {
        private int blocks;
        private int paragraphs;
        private int runs;
        private int tables;
        private int tableRows;
        private int tableCells;
        private int images;
        private int totalText;

        private void addBlock() throws WordParseException {
            blocks++;
            if (blocks > DocumentLimits.MAX_WORD_BLOCKS) {
                throw tooLarge("Word block limit exceeded", null);
            }
        }

        private void addParagraph() throws WordParseException {
            paragraphs++;
            if (paragraphs > DocumentLimits.MAX_WORD_PARAGRAPHS) {
                throw tooLarge("Word paragraph limit exceeded", null);
            }
        }

        private void addRun() throws WordParseException {
            runs++;
            if (runs > DocumentLimits.MAX_WORD_RUNS) {
                throw tooLarge("Word run limit exceeded", null);
            }
        }

        private void addTable() throws WordParseException {
            tables++;
            addBlock();
            if (tables > DocumentLimits.MAX_WORD_TABLES) {
                throw tooLarge("Word table limit exceeded", null);
            }
        }

        private void addTableRow() throws WordParseException {
            tableRows++;
            if (tableRows > DocumentLimits.MAX_WORD_TABLE_ROWS) {
                throw tooLarge("Word table row limit exceeded", null);
            }
        }

        private void addTableCell() throws WordParseException {
            tableCells++;
            if (tableCells > DocumentLimits.MAX_WORD_TABLE_CELLS) {
                throw tooLarge("Word table cell limit exceeded", null);
            }
        }

        private void addImage() throws WordParseException {
            images++;
            if (images > DocumentLimits.MAX_WORD_IMAGES) {
                throw tooLarge("Word image limit exceeded", null);
            }
        }

        private void addText(int count) throws WordParseException {
            if (count > DocumentLimits.MAX_WORD_TOTAL_CHARS - totalText) {
                throw tooLarge("Word text size limit exceeded", null);
            }
            totalText += count;
        }
    }
}
