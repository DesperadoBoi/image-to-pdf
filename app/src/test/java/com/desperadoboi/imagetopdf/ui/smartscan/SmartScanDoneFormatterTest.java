package com.desperadoboi.imagetopdf.ui.smartscan;

import com.desperadoboi.imagetopdf.util.PageCountFormatter;

import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SmartScanDoneFormatterTest {
    private static final Locale RUSSIAN = Locale.forLanguageTag("ru");
    private static final String LABEL_FORMAT = "Готово · %1$d";
    private static final String CONTENT_DESCRIPTION_FORMAT =
            "Завершить сканирование, %1$s";
    private static final PageCountFormatter.Labels PAGE_COUNT_LABELS =
            new PageCountFormatter.Labels("страница", "страницы", "страниц");

    @Test
    public void zeroPagesHidesButton() {
        SmartScanDoneFormatter.State state = format(0);

        assertFalse(state.isVisible());
        assertEquals("", state.getLabel());
        assertEquals("", state.getContentDescription());
    }

    @Test
    public void formatsCompactButtonLabel() {
        assertLabel(1, "Готово · 1");
        assertLabel(2, "Готово · 2");
        assertLabel(12, "Готово · 12");
        assertLabel(99, "Готово · 99");
    }

    @Test
    public void formatsAccessiblePageCount() {
        assertContentDescription(1, "Завершить сканирование, 1 страница");
        assertContentDescription(2, "Завершить сканирование, 2 страницы");
        assertContentDescription(5, "Завершить сканирование, 5 страниц");
        assertContentDescription(21, "Завершить сканирование, 21 страница");
    }

    private static void assertLabel(int pageCount, String expected) {
        SmartScanDoneFormatter.State state = format(pageCount);
        assertTrue(state.isVisible());
        assertEquals(expected, state.getLabel());
    }

    private static void assertContentDescription(int pageCount, String expected) {
        SmartScanDoneFormatter.State state = format(pageCount);
        assertTrue(state.isVisible());
        assertEquals(expected, state.getContentDescription());
    }

    private static SmartScanDoneFormatter.State format(int pageCount) {
        return SmartScanDoneFormatter.format(
                pageCount,
                RUSSIAN,
                LABEL_FORMAT,
                CONTENT_DESCRIPTION_FORMAT,
                PAGE_COUNT_LABELS
        );
    }
}
