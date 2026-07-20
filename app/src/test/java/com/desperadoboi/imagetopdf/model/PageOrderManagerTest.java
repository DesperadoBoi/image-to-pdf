package com.desperadoboi.imagetopdf.model;

import android.net.FakeUri;
import android.net.Uri;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class PageOrderManagerTest {
    @Test
    public void movesFirstPageToLastPosition() {
        List<String> pages = new ArrayList<>(Arrays.asList("A", "B", "C", "D"));

        assertTrue(PageOrderManager.move(pages, 0, 3));

        assertEquals(Arrays.asList("B", "C", "D", "A"), pages);
    }

    @Test
    public void movesLastPageToFirstPosition() {
        List<String> pages = new ArrayList<>(Arrays.asList("A", "B", "C", "D"));

        assertTrue(PageOrderManager.move(pages, 3, 0));

        assertEquals(Arrays.asList("D", "A", "B", "C"), pages);
    }

    @Test
    public void movesPageOnePositionUp() {
        List<String> pages = new ArrayList<>(Arrays.asList("A", "B", "C"));

        assertTrue(PageOrderManager.move(pages, 2, 1));

        assertEquals(Arrays.asList("A", "C", "B"), pages);
    }

    @Test
    public void movesPageOnePositionDown() {
        List<String> pages = new ArrayList<>(Arrays.asList("A", "B", "C"));

        assertTrue(PageOrderManager.move(pages, 1, 2));

        assertEquals(Arrays.asList("A", "C", "B"), pages);
    }

    @Test
    public void movingPageToSamePositionKeepsOrder() {
        List<String> pages = new ArrayList<>(Arrays.asList("A", "B", "C"));

        assertFalse(PageOrderManager.move(pages, 1, 1));

        assertEquals(Arrays.asList("A", "B", "C"), pages);
    }

    @Test
    public void invalidFromPositionIsRejected() {
        List<String> pages = new ArrayList<>(Arrays.asList("A", "B"));

        assertThrows(IndexOutOfBoundsException.class, () -> PageOrderManager.move(pages, -1, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> PageOrderManager.move(pages, 2, 0));
    }

    @Test
    public void invalidToPositionIsRejected() {
        List<String> pages = new ArrayList<>(Arrays.asList("A", "B"));

        assertThrows(IndexOutOfBoundsException.class, () -> PageOrderManager.move(pages, 0, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> PageOrderManager.move(pages, 0, 2));
    }

    @Test
    public void pageItemKeepsUriAndRotationAfterMove() {
        Uri firstUri = FakeUri.create("content://test/first");
        Uri secondUri = FakeUri.create("content://test/second");
        PageItem firstPage = new PageItem(firstUri, 90);
        PageItem secondPage = new PageItem(secondUri, 180);
        List<PageItem> pages = new ArrayList<>(Arrays.asList(firstPage, secondPage));

        PageOrderManager.move(pages, 0, 1);

        assertSame(secondPage, pages.get(0));
        assertSame(firstPage, pages.get(1));
        assertEquals(firstPage.getId(), pages.get(1).getId());
        assertSame(firstUri, pages.get(1).getImageUri());
        assertEquals(90, pages.get(1).getManualRotationDegrees());
    }
}
