package com.desperadoboi.imagetopdf.ui.watermark;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class PageRangeParserTest {
    private final PageRangeParser parser = new PageRangeParser();

    @Test public void parsesSinglePage() { assertPages("1", 8, 0); }
    @Test public void parsesRange() { assertPages("1-5", 8, 0, 1, 2, 3, 4); }
    @Test public void parsesCommaList() { assertPages("1,3,5", 8, 0, 2, 4); }
    @Test public void parsesMixedInput() { assertPages("1-3,5,7-9", 9, 0,1,2,4,6,7,8); }
    @Test public void removesDuplicates() { assertPages("1,1-3,2", 4, 0, 1, 2); }
    @Test public void acceptsWhitespace() { assertPages(" 1 , 3 - 5 , 8 ", 8, 0,2,3,4,7); }

    @Test public void rejectsZero() { assertError("0", 8, PageRangeParser.Error.NON_POSITIVE); }
    @Test public void rejectsNegative() { assertError("-1", 8, PageRangeParser.Error.MALFORMED); }
    @Test public void rejectsReversedRange() { assertError("5-3", 8, PageRangeParser.Error.REVERSED_RANGE); }
    @Test public void rejectsPageAboveMaximum() { assertError("9", 8, PageRangeParser.Error.ABOVE_MAXIMUM); }
    @Test public void rejectsMalformedInput() { assertError("1,,3", 8, PageRangeParser.Error.MALFORMED); }
    @Test public void rejectsLetters() { assertError("1,a", 8, PageRangeParser.Error.MALFORMED); }
    @Test public void rejectsEmptyInput() { assertError("  ", 8, PageRangeParser.Error.EMPTY); }

    @Test public void normalizesAndCompactsResult() {
        PageRangeParser.Result result = parser.parse("5, 1,2,3,8,8", 8);
        assertTrue(result.isValid());
        assertEquals("1-3,5,8", result.normalized());
    }

    private void assertPages(String value, int maximum, Integer... expected) {
        PageRangeParser.Result result = parser.parse(value, maximum);
        assertTrue(result.getError().name(), result.isValid());
        assertEquals(Arrays.asList(expected), result.getZeroBasedPages());
    }

    private void assertError(String value, int maximum, PageRangeParser.Error expected) {
        PageRangeParser.Result result = parser.parse(value, maximum);
        assertFalse(result.isValid());
        assertEquals(expected, result.getError());
        assertTrue(result.getZeroBasedPages().isEmpty());
    }
}
