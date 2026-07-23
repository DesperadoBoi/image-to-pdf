package com.desperadoboi.imagetopdf.document.word;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class WordStyleResolverTest {
    @Test
    public void resolvesEachPropertyInCanonicalPrecedenceOrder() throws Exception {
        Map<String, WordStyleResolver.Definition> definitions = new HashMap<>();
        definitions.put("Base", definition(
                "Base",
                "paragraph",
                null,
                null,
                new WordParagraphStyle.Builder()
                        .setAlignment(WordParagraphStyle.Alignment.RIGHT)
                        .setSpaceAfterTwips(120)
                        .build(),
                new WordRunStyle.Builder().setBold(true).build()
        ));
        definitions.put("Linked", definition(
                "Linked",
                "character",
                null,
                null,
                null,
                new WordRunStyle.Builder().setItalic(true).build()
        ));
        definitions.put("Child", definition(
                "Child",
                "paragraph",
                "Base",
                "Linked",
                new WordParagraphStyle.Builder().setSpaceAfterTwips(240).build(),
                new WordRunStyle.Builder().setFontSizePoints(12f).build()
        ));
        definitions.put("RunStyle", definition(
                "RunStyle",
                "character",
                null,
                null,
                null,
                new WordRunStyle.Builder().setUnderline(true).build()
        ));
        WordStyleResolver resolver = new WordStyleResolver(
                new WordRunStyle.Builder()
                        .setFontSizePoints(10f)
                        .setBold(false)
                        .setItalic(false)
                        .setUnderline(false)
                        .build(),
                WordParagraphStyle.defaults(),
                definitions,
                true
        );

        WordStyleResolver.ResolvedParagraph paragraph =
                resolver.resolveParagraph("Child");
        WordRunStyle run = resolver.resolveRun(
                paragraph,
                "RunStyle",
                new WordRunStyle.Builder().setFontSizePoints(14f).build(),
                new WordRunStyle.Builder()
                        .setFontSizePoints(16f)
                        .setBold(false)
                        .build()
        );

        assertEquals(WordParagraphStyle.Alignment.RIGHT,
                paragraph.getParagraphStyle().getAlignment());
        assertEquals(240, paragraph.getParagraphStyle().getSpaceAfterTwips());
        assertEquals("Child", paragraph.getParagraphStyle().getStyleId());
        assertEquals(16f, run.getFontSizePoints(), 0.001f);
        assertFalse(run.isBold());
        assertTrue(run.isItalic());
        assertTrue(run.isUnderline());
        String debug = resolver.debugResolvedTypography(paragraph, run);
        assertTrue(debug.contains("styleId=Child"));
        assertTrue(debug.contains("fontPt=16.0"));
        assertFalse(debug.contains("document text"));
    }

    @Test
    public void missingStyleFallsBackAndRepeatedResolutionIsStable() throws Exception {
        WordStyleResolver resolver = new WordStyleResolver(
                WordRunStyle.defaults(),
                WordParagraphStyle.defaults(),
                new HashMap<>(),
                false
        );
        WordStyleResolver.ResolvedParagraph first =
                resolver.resolveParagraph("Missing");
        WordStyleResolver.ResolvedParagraph second =
                resolver.resolveParagraph("Missing");

        assertEquals(11f, first.getRunStyle().getFontSizePoints(), 0.001f);
        assertEquals(first.getParagraphStyle().getAlignment(),
                second.getParagraphStyle().getAlignment());
        assertEquals("", resolver.debugResolvedTypography(first, first.getRunStyle()));
    }

    @Test
    public void protectsParagraphAndCharacterInheritanceFromCycles() throws Exception {
        Map<String, WordStyleResolver.Definition> definitions = new HashMap<>();
        definitions.put("A", definition("A", "paragraph", "B", null, null, null));
        definitions.put("B", definition("B", "paragraph", "A", null, null, null));
        definitions.put("C", definition("C", "character", "D", null, null, null));
        definitions.put("D", definition("D", "character", "C", null, null, null));
        WordStyleResolver resolver = new WordStyleResolver(
                WordRunStyle.defaults(),
                WordParagraphStyle.defaults(),
                definitions,
                false
        );

        assertCycle(() -> resolver.resolveParagraph("A"));
        WordStyleResolver.ResolvedParagraph paragraph =
                resolver.resolveParagraph(null);
        assertCycle(() -> resolver.resolveRun(paragraph, "C", null, null));
    }

    private static WordStyleResolver.Definition definition(
            String id,
            String type,
            String basedOn,
            String linked,
            WordParagraphStyle paragraph,
            WordRunStyle run
    ) {
        return new WordStyleResolver.Definition(
                id,
                type,
                basedOn,
                linked,
                id,
                paragraph,
                run
        );
    }

    private static void assertCycle(ThrowingRunnable runnable) throws Exception {
        try {
            runnable.run();
            fail("Expected style cycle");
        } catch (WordParseException exception) {
            assertEquals(
                    WordParseException.Reason.CORRUPTED,
                    exception.getReason()
            );
        }
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
