package com.desperadoboi.imagetopdf.model;

import android.net.FakeUri;
import android.net.Uri;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ImageSelectionTest {
    private static final Uri FIRST = FakeUri.create("content://test/first");
    private static final Uri SECOND = FakeUri.create("content://test/second");
    private static final Uri THIRD = FakeUri.create("content://test/third");

    @Test
    public void emptySelectionHasImmutableEmptySnapshot() {
        ImageSelection selection = new ImageSelection();

        assertTrue(selection.isEmpty());
        assertEquals(0, selection.size());
        assertThrows(UnsupportedOperationException.class, () -> selection.snapshot().add(FIRST));
    }

    @Test
    public void selectAndDeselectUpdateMembership() {
        ImageSelection selection = new ImageSelection();

        assertTrue(selection.select(FIRST));
        assertTrue(selection.isSelected(FIRST));
        assertEquals(1, selection.getSelectionNumber(FIRST));
        assertTrue(selection.deselect(FIRST));
        assertFalse(selection.isSelected(FIRST));
    }

    @Test
    public void selectionOrderIsPreserved() {
        ImageSelection selection = new ImageSelection();
        selection.select(SECOND);
        selection.select(FIRST);
        selection.select(THIRD);

        assertUriOrder(selection.snapshot(), SECOND, FIRST, THIRD);
    }

    @Test
    public void reselectGoesToEndWithoutChangingOtherOrder() {
        ImageSelection selection = new ImageSelection();
        selection.select(FIRST);
        selection.select(SECOND);
        selection.select(THIRD);

        selection.toggle(SECOND);
        selection.toggle(SECOND);

        assertUriOrder(selection.snapshot(), FIRST, THIRD, SECOND);
    }

    @Test
    public void duplicateSelectionIsPrevented() {
        ImageSelection selection = new ImageSelection();

        assertTrue(selection.select(FIRST));
        assertFalse(selection.select(FIRST));
        assertUriOrder(selection.snapshot(), FIRST);
    }

    @Test
    public void unrelatedAlbumSwitchDoesNotChangeSelection() {
        ImageSelection selection = new ImageSelection();
        selection.select(FIRST);
        String currentAlbum = "Camera";
        currentAlbum = "Screenshots";

        assertEquals("Screenshots", currentAlbum);
        assertUriOrder(selection.snapshot(), FIRST);
    }

    @Test
    public void snapshotDoesNotChangeWithLaterSelection() {
        ImageSelection selection = new ImageSelection();
        selection.select(FIRST);
        List<Uri> snapshot = selection.snapshot();

        selection.select(SECOND);

        assertUriOrder(snapshot, FIRST);
        assertUriOrder(selection.snapshot(), FIRST, SECOND);
    }

    private void assertUriOrder(List<Uri> actual, Uri... expected) {
        assertEquals(expected.length, actual.size());
        for (int index = 0; index < expected.length; index++) {
            assertSame(expected[index], actual.get(index));
        }
    }
}
