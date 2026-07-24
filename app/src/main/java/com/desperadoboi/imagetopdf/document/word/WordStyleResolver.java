package com.desperadoboi.imagetopdf.document.word;

import com.desperadoboi.imagetopdf.document.DocumentLimits;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Resolves Word properties independently in this order: document defaults, paragraph
 * basedOn chain, named paragraph style, linked character style, run style, direct
 * paragraph run properties, and direct run properties.
 */
public final class WordStyleResolver {
    private final WordRunStyle documentRun;
    private final WordParagraphStyle documentParagraph;
    private final Map<String, Definition> definitions;
    private final Map<String, ResolvedParagraph> paragraphCache = new HashMap<>();
    private final Map<String, WordRunStyle> characterCache = new HashMap<>();
    private final boolean debugTypographyEnabled;

    WordStyleResolver(
            WordRunStyle documentRun,
            WordParagraphStyle documentParagraph,
            Map<String, Definition> definitions,
            boolean debugTypographyEnabled
    ) {
        this.documentRun = documentRun == null
                ? WordRunStyle.defaults()
                : documentRun;
        this.documentParagraph = documentParagraph == null
                ? WordParagraphStyle.defaults()
                : documentParagraph;
        this.definitions = new HashMap<>(definitions);
        this.debugTypographyEnabled = debugTypographyEnabled;
    }

    ResolvedParagraph resolveParagraph(String id) throws WordParseException {
        if (id == null || id.isEmpty()) {
            return new ResolvedParagraph(documentParagraph, documentRun);
        }
        ResolvedParagraph cached = paragraphCache.get(id);
        if (cached != null) return cached;
        ResolvedParagraph resolved = resolveParagraph(id, new HashSet<>(), 0);
        paragraphCache.put(id, resolved);
        return resolved;
    }

    WordRunStyle resolveRun(
            ResolvedParagraph paragraph,
            String runStyleId,
            WordRunStyle directParagraphRun,
            WordRunStyle directRun
    ) throws WordParseException {
        WordRunStyle resolved = paragraph == null
                ? documentRun
                : paragraph.runStyle;
        resolved = WordRunStyle.merge(resolved, resolveCharacter(runStyleId));
        resolved = WordRunStyle.merge(resolved, directParagraphRun);
        return WordRunStyle.merge(resolved, directRun);
    }

    WordRunStyle resolveParagraphDefaultRun(
            ResolvedParagraph paragraph,
            WordRunStyle directParagraphRun
    ) {
        return WordRunStyle.merge(
                paragraph == null ? documentRun : paragraph.runStyle,
                directParagraphRun
        );
    }

    String debugResolvedTypography(
            ResolvedParagraph paragraph,
            WordRunStyle run
    ) {
        if (!debugTypographyEnabled) return "";
        WordParagraphStyle paragraphStyle = paragraph == null
                ? documentParagraph
                : paragraph.paragraphStyle;
        WordRunStyle runStyle = run == null
                ? (paragraph == null ? documentRun : paragraph.runStyle)
                : run;
        return "styleId=" + paragraphStyle.getStyleId()
                + ",heading=" + paragraphStyle.getHeadingLevel()
                + ",fontPt=" + runStyle.getFontSizePoints()
                + ",bold=" + runStyle.isBold()
                + ",italic=" + runStyle.isItalic()
                + ",beforeTwips=" + paragraphStyle.getSpaceBeforeTwips()
                + ",afterTwips=" + paragraphStyle.getSpaceAfterTwips()
                + ",lineRule=" + paragraphStyle.getLineRule()
                + ",lineValue=" + paragraphStyle.getLineSpacingValue();
    }

    private ResolvedParagraph resolveParagraph(
            String id,
            Set<String> visiting,
            int depth
    ) throws WordParseException {
        String visitKey = "p:" + id;
        ensureAcyclic(visiting, visitKey, depth, "paragraph");
        Definition definition = definitions.get(id);
        if (definition == null || "character".equals(definition.type)) {
            visiting.remove(visitKey);
            return new ResolvedParagraph(documentParagraph, documentRun);
        }
        ResolvedParagraph base = definition.basedOn == null
                ? new ResolvedParagraph(documentParagraph, documentRun)
                : resolveParagraph(definition.basedOn, visiting, depth + 1);
        WordParagraphStyle ownParagraph = WordParagraphStyle.merge(
                new WordParagraphStyle.Builder().build(),
                definition.paragraphStyle
        );
        ownParagraph = WordParagraphStyle.merge(
                ownParagraph,
                new WordParagraphStyle.Builder().setStyleId(id).build()
        );
        WordParagraphStyle paragraph = WordParagraphStyle.merge(
                base.paragraphStyle,
                ownParagraph
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
        if (definition.linkedStyle != null) {
            run = WordRunStyle.merge(
                    run,
                    resolveCharacter(definition.linkedStyle, visiting, depth + 1)
            );
        }
        visiting.remove(visitKey);
        return new ResolvedParagraph(paragraph, run);
    }

    private WordRunStyle resolveCharacter(String id) throws WordParseException {
        if (id == null || id.isEmpty()) return null;
        if (characterCache.containsKey(id)) return characterCache.get(id);
        WordRunStyle result = resolveCharacter(id, new HashSet<>(), 0);
        if (result != null) characterCache.put(id, result);
        return result;
    }

    private WordRunStyle resolveCharacter(
            String id,
            Set<String> visiting,
            int depth
    ) throws WordParseException {
        String visitKey = "c:" + id;
        ensureAcyclic(visiting, visitKey, depth, "character");
        Definition definition = definitions.get(id);
        if (definition == null) {
            visiting.remove(visitKey);
            return null;
        }
        if ("paragraph".equals(definition.type) && definition.linkedStyle != null) {
            WordRunStyle linked = resolveCharacter(
                    definition.linkedStyle,
                    visiting,
                    depth + 1
            );
            visiting.remove(visitKey);
            return linked;
        }
        WordRunStyle base = definition.basedOn == null
                ? null
                : resolveCharacter(definition.basedOn, visiting, depth + 1);
        WordRunStyle result = WordRunStyle.merge(base, definition.runStyle);
        visiting.remove(visitKey);
        return result;
    }

    private void ensureAcyclic(
            Set<String> visiting,
            String key,
            int depth,
            String type
    ) throws WordParseException {
        if (depth > DocumentLimits.MAX_WORD_STYLE_DEPTH || !visiting.add(key)) {
            throw new WordParseException(
                    WordParseException.Reason.CORRUPTED,
                    "Cyclic Word " + type + " style inheritance"
            );
        }
    }

    private static int headingLevel(String styleId, String styleName) {
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

    static final class Definition {
        private final String id;
        private final String type;
        private final String basedOn;
        private final String linkedStyle;
        private final String name;
        private final WordParagraphStyle paragraphStyle;
        private final WordRunStyle runStyle;

        Definition(
                String id,
                String type,
                String basedOn,
                String linkedStyle,
                String name,
                WordParagraphStyle paragraphStyle,
                WordRunStyle runStyle
        ) {
            this.id = id;
            this.type = type;
            this.basedOn = basedOn;
            this.linkedStyle = linkedStyle;
            this.name = name;
            this.paragraphStyle = paragraphStyle;
            this.runStyle = runStyle;
        }

        String getId() {
            return id;
        }
    }

    static final class ResolvedParagraph {
        private final WordParagraphStyle paragraphStyle;
        private final WordRunStyle runStyle;

        private ResolvedParagraph(
                WordParagraphStyle paragraphStyle,
                WordRunStyle runStyle
        ) {
            this.paragraphStyle = paragraphStyle;
            this.runStyle = runStyle;
        }

        WordParagraphStyle getParagraphStyle() {
            return paragraphStyle;
        }

        WordRunStyle getRunStyle() {
            return runStyle;
        }
    }
}
