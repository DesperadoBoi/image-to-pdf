package com.desperadoboi.imagetopdf.ui.editor;

import java.util.List;

public final class SelectedPageResolver {
    public static final long NO_PAGE_ID = -1L;

    private SelectedPageResolver() {
    }

    public static long selectAfterDeletion(List<Long> remainingPageIds, int removedPosition) {
        if (remainingPageIds.isEmpty()) {
            return NO_PAGE_ID;
        }
        return remainingPageIds.get(Math.min(removedPosition, remainingPageIds.size() - 1));
    }
}
