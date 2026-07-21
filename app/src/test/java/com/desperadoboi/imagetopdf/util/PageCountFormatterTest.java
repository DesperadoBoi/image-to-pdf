package com.desperadoboi.imagetopdf.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PageCountFormatterTest {
    private static final PageCountFormatter.Labels LABELS =
            new PageCountFormatter.Labels("страница", "страницы", "страниц");

    @Test
    public void formatsRussianPageCountForms() {
        assertEquals("1 страница", format(1));
        assertEquals("2 страницы", format(2));
        assertEquals("4 страницы", format(4));
        assertEquals("5 страниц", format(5));
        assertEquals("11 страниц", format(11));
        assertEquals("21 страница", format(21));
        assertEquals("22 страницы", format(22));
        assertEquals("25 страниц", format(25));
    }

    private static String format(int pageCount) {
        return PageCountFormatter.format(pageCount, LABELS);
    }
}
