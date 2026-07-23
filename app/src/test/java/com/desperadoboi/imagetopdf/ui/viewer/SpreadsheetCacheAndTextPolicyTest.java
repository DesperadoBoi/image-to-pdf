package com.desperadoboi.imagetopdf.ui.viewer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class SpreadsheetCacheAndTextPolicyTest {
    @Test
    public void boundedLruCacheEvictsLeastRecentlyUsedEntry() {
        SpreadsheetBoundedLruCache<Integer, String> cache =
                new SpreadsheetBoundedLruCache<>(2);
        cache.put(1, "one");
        cache.put(2, "two");
        assertEquals("one", cache.get(1));

        cache.put(3, "three");

        assertNull(cache.get(2));
        assertEquals("one", cache.get(1));
        assertEquals("three", cache.get(3));
        assertEquals(2, cache.size());
        assertEquals(256, SpreadsheetPaintCache.MAXIMUM_STYLE_COUNT);
        assertEquals(96, SpreadsheetTextLayoutCache.MAXIMUM_LAYOUT_COUNT);
    }

    @Test
    public void overviewNeverRequestsWrappedLayout() {
        assertFalse(SpreadsheetLevelOfDetailPolicy.shouldBuildWrappedLayout(
                0.25f,
                200f,
                80f,
                false,
                true
        ));
        assertFalse(SpreadsheetLevelOfDetailPolicy.shouldBuildWrappedLayout(
                1f,
                200f,
                80f,
                true,
                true
        ));
        assertTrue(SpreadsheetLevelOfDetailPolicy.shouldBuildWrappedLayout(
                1f,
                200f,
                80f,
                false,
                true
        ));
    }

    @Test
    public void layoutBucketsAvoidRebuildingForTinyPinchDeltas() {
        assertEquals(
                SpreadsheetTextLayoutCache.zoomBucket(1f),
                SpreadsheetTextLayoutCache.zoomBucket(1.01f)
        );
        assertEquals(
                SpreadsheetTextLayoutCache.widthBucket(100),
                SpreadsheetTextLayoutCache.widthBucket(101)
        );
    }

    @Test
    public void mandatoryCellClipProtectsNonEmptyNeighbour() {
        float firstCellRight = 100f;
        float clippedTextRight = SpreadsheetTextClipPolicy.contentEnd(
                0f,
                firstCellRight,
                4f
        );

        assertTrue(clippedTextRight < firstCellRight);
        assertTrue(SpreadsheetTextClipPolicy.isDrawable(4f, clippedTextRight));
    }

    @Test
    public void multilineValueUsesBoundedSingleLineSanitizerCache() {
        SpreadsheetSingleLineTextCache cache = new SpreadsheetSingleLineTextCache();

        assertEquals("first second third", cache.get("first\nsecond\rthird"));
        assertEquals(1, cache.size());
        assertEquals(128, SpreadsheetSingleLineTextCache.MAXIMUM_ENTRY_COUNT);
    }
}
