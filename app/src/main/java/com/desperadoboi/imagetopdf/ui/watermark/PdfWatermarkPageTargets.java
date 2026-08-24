package com.desperadoboi.imagetopdf.ui.watermark;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class PdfWatermarkPageTargets {
    private PdfWatermarkPageTargets() {
    }

    public static Set<Integer> resolve(
            WatermarkPageSelection selection,
            int currentPage,
            int pageCount,
            PageRangeParser.Result range
    ) {
        if (selection == null || pageCount <= 0) return Collections.emptySet();
        LinkedHashSet<Integer> result = new LinkedHashSet<>();
        if (selection == WatermarkPageSelection.ALL) {
            for (int page = 0; page < pageCount; page++) result.add(page);
        } else if (selection == WatermarkPageSelection.CURRENT) {
            result.add(Math.max(0, Math.min(currentPage, pageCount - 1)));
        } else if (range != null && range.isValid()) {
            result.addAll(range.getZeroBasedPages());
        }
        return Collections.unmodifiableSet(result);
    }
}
