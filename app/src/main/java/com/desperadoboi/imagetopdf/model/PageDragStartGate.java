package com.desperadoboi.imagetopdf.model;

public final class PageDragStartGate {
    public static final long NO_ACTIVE_PAGE_ID = -1L;

    private long activePageId = NO_ACTIVE_PAGE_ID;

    public boolean tryStart(long pageId) {
        if (pageId < 0L || activePageId != NO_ACTIVE_PAGE_ID) {
            return false;
        }
        activePageId = pageId;
        return true;
    }

    public void finish(long pageId) {
        if (activePageId == pageId) {
            activePageId = NO_ACTIVE_PAGE_ID;
        }
    }

    public long getActivePageId() {
        return activePageId;
    }
}
