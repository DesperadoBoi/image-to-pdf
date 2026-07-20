package com.desperadoboi.imagetopdf.ui.editor;

import android.net.FakeUri;
import android.net.Uri;

import com.desperadoboi.imagetopdf.model.PageItem;
import com.desperadoboi.imagetopdf.model.PageOrderManager;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class PreviewPageNavigatorTest {
    @Test
    public void firstPageHasNoPrevious() {
        List<PageItem> pages = createPages("first", "second");

        assertFalse(PreviewPageNavigator.hasPrevious(pages, pages.get(0).getId()));
        assertTrue(PreviewPageNavigator.hasNext(pages, pages.get(0).getId()));
    }

    @Test
    public void lastPageHasNoNext() {
        List<PageItem> pages = createPages("first", "second");

        assertTrue(PreviewPageNavigator.hasPrevious(pages, pages.get(1).getId()));
        assertFalse(PreviewPageNavigator.hasNext(pages, pages.get(1).getId()));
    }

    @Test
    public void movesForwardByStableId() {
        List<PageItem> pages = createPages("first", "second", "third");

        long nextId = PreviewPageNavigator.nextPageId(pages, pages.get(0).getId());

        assertEquals(pages.get(1).getId(), nextId);
    }

    @Test
    public void movesBackwardByStableId() {
        List<PageItem> pages = createPages("first", "second", "third");

        long previousId = PreviewPageNavigator.previousPageId(pages, pages.get(2).getId());

        assertEquals(pages.get(1).getId(), previousId);
    }

    @Test
    public void findsCurrentPositionByStableId() {
        List<PageItem> pages = createPages("first", "second", "third");

        assertEquals(1, PreviewPageNavigator.findPositionById(pages, pages.get(1).getId()));
    }

    @Test
    public void unknownStableIdReturnsNotFound() {
        List<PageItem> pages = createPages("first", "second");

        assertEquals(
                PreviewPageNavigator.POSITION_NOT_FOUND,
                PreviewPageNavigator.findPositionById(pages, Long.MAX_VALUE)
        );
    }

    @Test
    public void reorderedPageIsFoundAtNewPositionByStableId() {
        List<PageItem> pages = new ArrayList<>(createPages("first", "second", "third"));
        long firstPageId = pages.get(0).getId();

        PageOrderManager.move(pages, 0, 2);

        assertEquals(2, PreviewPageNavigator.findPositionById(pages, firstPageId));
    }

    @Test
    public void pagesWithSameUriHaveDifferentStableIds() {
        Uri sharedUri = FakeUri.create("content://test/shared");
        List<PageItem> pages = Arrays.asList(new PageItem(sharedUri), new PageItem(sharedUri));

        assertNotEquals(pages.get(0).getId(), pages.get(1).getId());
        assertEquals(0, PreviewPageNavigator.findPositionById(pages, pages.get(0).getId()));
        assertEquals(1, PreviewPageNavigator.findPositionById(pages, pages.get(1).getId()));
    }

    private List<PageItem> createPages(String... names) {
        List<PageItem> pages = new ArrayList<>();
        for (String name : names) {
            pages.add(new PageItem(FakeUri.create("content://test/" + name)));
        }
        return pages;
    }
}
