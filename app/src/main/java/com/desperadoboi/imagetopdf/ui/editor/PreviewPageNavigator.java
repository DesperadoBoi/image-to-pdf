package com.desperadoboi.imagetopdf.ui.editor;

import com.desperadoboi.imagetopdf.model.PageItem;

import java.util.List;

public final class PreviewPageNavigator {
    public static final int POSITION_NOT_FOUND = -1;

    private PreviewPageNavigator() {
    }

    public static int findPositionById(List<PageItem> pages, long pageId) {
        if (pages == null || pages.isEmpty()) {
            return POSITION_NOT_FOUND;
        }
        for (int index = 0; index < pages.size(); index++) {
            if (pages.get(index).getId() == pageId) {
                return index;
            }
        }
        return POSITION_NOT_FOUND;
    }

    public static boolean hasPrevious(List<PageItem> pages, long pageId) {
        return findPositionById(pages, pageId) > 0;
    }

    public static boolean hasNext(List<PageItem> pages, long pageId) {
        int position = findPositionById(pages, pageId);
        return position != POSITION_NOT_FOUND && position < pages.size() - 1;
    }

    public static long previousPageId(List<PageItem> pages, long pageId) {
        int position = findPositionById(pages, pageId);
        if (position <= 0) {
            throw new IllegalArgumentException("Page has no previous item");
        }
        return pages.get(position - 1).getId();
    }

    public static long nextPageId(List<PageItem> pages, long pageId) {
        int position = findPositionById(pages, pageId);
        if (position == POSITION_NOT_FOUND || position >= pages.size() - 1) {
            throw new IllegalArgumentException("Page has no next item");
        }
        return pages.get(position + 1).getId();
    }
}
