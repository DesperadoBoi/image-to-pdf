package com.desperadoboi.imagetopdf.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class PdfExportSummaryTest {
    private static final PdfExportSummary.Labels LABELS = new PdfExportSummary.Labels(
            "По изображению",
            "A4",
            "Авто",
            "Книжная",
            "Альбомная",
            "Без полей",
            "Небольшие",
            "Стандартные поля"
    );

    @Test
    public void defaultDraftUsesCompactDefaultSummary() {
        PdfExportDraft draft = PdfExportDraft.defaults("Document");

        assertEquals(
                "По изображению · Авто · Стандартные поля",
                PdfExportSummary.from(draft).format(LABELS)
        );
    }

    @Test
    public void imageAutoAndStandardValuesBuildExpectedSummary() {
        PdfExportSummary summary = new PdfExportSummary(
                PageSizeMode.IMAGE,
                PdfOrientationMode.AUTO,
                MarginPreset.STANDARD
        );

        assertEquals(
                "По изображению · Авто · Стандартные поля",
                summary.format(LABELS)
        );
    }

    @Test
    public void a4PortraitAndNoMarginsBuildExpectedSummary() {
        PdfExportSummary summary = new PdfExportSummary(
                PageSizeMode.A4,
                PdfOrientationMode.PORTRAIT,
                MarginPreset.NONE
        );

        assertEquals("A4 · Книжная · Без полей", summary.format(LABELS));
    }

    @Test
    public void changedValuesChangeSummary() {
        PdfExportDraft changed = PdfExportDraft.defaults("Document")
                .withPageSize(PageSizeMode.A4)
                .withOrientation(PdfOrientationMode.LANDSCAPE)
                .withMargins(MarginPreset.SMALL);

        assertEquals(
                "A4 · Альбомная · Небольшие",
                PdfExportSummary.from(changed).format(LABELS)
        );
    }

    @Test
    public void nullSummaryValuesAreRejected() {
        assertThrows(
                NullPointerException.class,
                () -> new PdfExportSummary(null, PdfOrientationMode.AUTO, MarginPreset.STANDARD)
        );
        assertThrows(NullPointerException.class, () -> PdfExportSummary.from(null));
    }
}
