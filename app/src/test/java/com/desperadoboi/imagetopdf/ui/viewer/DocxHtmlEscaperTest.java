package com.desperadoboi.imagetopdf.ui.viewer;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class DocxHtmlEscaperTest {
    @Test
    public void escapesEveryHtmlAndAttributeMetacharacter() {
        assertEquals(
                "&lt;&gt;&amp;&quot;&#39;",
                DocxHtmlEscaper.text("<>&\"'")
        );
    }

    @Test
    public void preservesUnicodeLineBreaksTabsAndSpaces() {
        assertEquals(
                "Привет 😀\n  строка\tконец",
                DocxHtmlEscaper.text("Привет 😀\n  строка\tконец")
        );
    }

    @Test
    public void maliciousHtmlLikeTextRemainsText() {
        assertEquals(
                "&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt;",
                DocxHtmlEscaper.text("<script>alert('x')</script>")
        );
    }

    @Test
    public void nullAndEmptyValuesAreSafe() {
        assertEquals("", DocxHtmlEscaper.text(null));
        assertEquals("", DocxHtmlEscaper.attribute(""));
    }
}
