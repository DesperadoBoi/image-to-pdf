package com.desperadoboi.imagetopdf.document.pdf;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PdfPageStateTest {
    @Test
    public void clampsAndRestoresPageBounds() {
        PdfPageState state = new PdfPageState(5, 3);
        assertEquals(3, state.getCurrentPage());
        assertTrue(state.hasPrevious());
        assertTrue(state.hasNext());
        assertEquals(4, state.setCurrentPage(99));
        assertFalse(state.hasNext());
        assertEquals(0, state.setCurrentPage(-2));
    }

    @Test
    public void lruCacheEvictsLeastRecentlyUsedPage() {
        List<String> removed = new ArrayList<>();
        PageLruCache<String> cache = new PageLruCache<>(2, removed::add);
        cache.put(0, "zero");
        cache.put(1, "one");
        assertEquals("zero", cache.get(0));
        cache.put(2, "two");

        assertNull(cache.get(1));
        assertEquals("one", removed.get(0));
        assertEquals(2, cache.size());
    }
}
