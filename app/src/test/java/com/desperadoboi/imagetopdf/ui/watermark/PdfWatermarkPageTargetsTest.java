package com.desperadoboi.imagetopdf.ui.watermark;

import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class PdfWatermarkPageTargetsTest {
    @Test public void allPagesTargetsEveryPage() {
        assertEquals(set(0, 1, 2), PdfWatermarkPageTargets.resolve(
                WatermarkPageSelection.ALL, 1, 3, null
        ));
    }

    @Test public void currentPageTargetsOnlyCurrentPreviewPage() {
        assertEquals(set(2), PdfWatermarkPageTargets.resolve(
                WatermarkPageSelection.CURRENT, 2, 5, null
        ));
    }

    @Test public void customRangeTargetsNormalizedSelection() {
        PageRangeParser.Result parsed = new PageRangeParser().parse("1,3-4", 5);
        assertEquals(set(0, 2, 3), PdfWatermarkPageTargets.resolve(
                WatermarkPageSelection.RANGE, 1, 5, parsed
        ));
    }

    @Test public void invalidRangeTargetsNothing() {
        assertTrue(PdfWatermarkPageTargets.resolve(
                WatermarkPageSelection.RANGE,
                0,
                5,
                new PageRangeParser().parse("9", 5)
        ).isEmpty());
    }

    private static Set<Integer> set(Integer... values) {
        return new LinkedHashSet<>(Arrays.asList(values));
    }
}
