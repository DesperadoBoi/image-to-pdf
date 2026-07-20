package com.desperadoboi.imagetopdf.model;

import java.util.ArrayList;
import java.util.List;

public final class CapturedPageCleanup {
    private CapturedPageCleanup() {
    }

    public static List<String> collectCapturedFileNames(List<PageItem> pages) {
        ArrayList<String> capturedFileNames = new ArrayList<>();
        if (pages == null || pages.isEmpty()) {
            return capturedFileNames;
        }
        for (PageItem page : pages) {
            if (page != null && page.isAppOwnedCapture()) {
                capturedFileNames.add(page.getCapturedFileName());
            }
        }
        return capturedFileNames;
    }
}
