package com.desperadoboi.imagetopdf.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class PdfFileNameFormatterTest {
    @Test
    public void pdfExtensionIsNotDuplicated() {
        assertEquals("Document.pdf", PdfFileNameFormatter.normalizeFileName("Document.pdf"));
        assertEquals("Document.pdf", PdfFileNameFormatter.normalizeFileName("Document.PDF.pdf"));
    }

    @Test
    public void emptyNameIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PdfFileNameFormatter.normalizeFileName("  .pdf ")
        );
    }

    @Test
    public void invalidCharactersAreRemovedFromDocumentName() {
        assertEquals(
                "My_report_______.pdf",
                PdfFileNameFormatter.normalizeFileName("My/report:*?\"<>|")
        );
    }

    @Test
    public void displayTitleKeepsPdfExtensionTogether() {
        String displayTitle = PdfFileNameFormatter.toDisplayTitle("Document.pdf");

        assertEquals("Document\u2060.pdf", displayTitle);
        assertTrue(displayTitle.endsWith(".pdf"));
    }

    @Test
    public void veryLongNameIsShortenedAndKeepsBothEnds() {
        String longName = "A".repeat(90) + "important-ending";

        String normalized = PdfFileNameFormatter.normalizeFileName(longName);

        assertEquals(
                PdfFileNameFormatter.MAX_BASE_NAME_LENGTH + ".pdf".length(),
                normalized.length()
        );
        assertTrue(normalized.startsWith("AAAA"));
        assertTrue(normalized.endsWith("important-ending.pdf"));
        assertTrue(normalized.contains("\u2026"));
    }

    @Test
    public void editableNameDoesNotExposeExtension() {
        assertEquals("Document", PdfFileNameFormatter.toEditableName("Document.pdf"));
    }
}
