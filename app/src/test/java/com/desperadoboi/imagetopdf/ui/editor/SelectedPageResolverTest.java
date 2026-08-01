package com.desperadoboi.imagetopdf.ui.editor;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public final class SelectedPageResolverTest {
    @Test public void selectsPageAtRemovedPositionWhenAvailable() {
        assertEquals(30L, SelectedPageResolver.selectAfterDeletion(Arrays.asList(10L, 30L, 40L), 1));
    }

    @Test public void selectsPreviousPageWhenLastPageWasRemoved() {
        assertEquals(20L, SelectedPageResolver.selectAfterDeletion(Arrays.asList(10L, 20L), 2));
    }

    @Test public void returnsNoPageForEmptyDocument() {
        assertEquals(SelectedPageResolver.NO_PAGE_ID, SelectedPageResolver.selectAfterDeletion(Collections.emptyList(), 0));
    }
}
