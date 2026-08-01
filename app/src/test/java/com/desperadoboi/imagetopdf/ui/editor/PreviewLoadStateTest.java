package com.desperadoboi.imagetopdf.ui.editor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class PreviewLoadStateTest {
    @Test public void noPagesHasHighestPriority() {
        PreviewLoadState state = new PreviewLoadState();
        state.start("page-1");
        state.showEmpty();

        assertEquals(PreviewLoadState.Status.EMPTY, state.getStatus());
        assertFalse(state.isCurrent("page-1"));
    }

    @Test public void loadingErrorAndLoadedAreMutuallyExclusiveStatuses() {
        PreviewLoadState state = new PreviewLoadState();

        assertTrue(state.start("page-1"));
        assertEquals(PreviewLoadState.Status.LOADING, state.getStatus());
        assertTrue(state.failed("page-1"));
        assertEquals(PreviewLoadState.Status.ERROR, state.getStatus());
        assertTrue(state.start("page-1"));
        assertTrue(state.loaded("page-1"));
        assertEquals(PreviewLoadState.Status.LOADED, state.getStatus());
    }

    @Test public void parallelRetryForSamePageIsRejected() {
        PreviewLoadState state = new PreviewLoadState();

        assertTrue(state.start("page-1"));
        assertFalse(state.start("page-1"));
        assertEquals(PreviewLoadState.Status.LOADING, state.getStatus());
    }

    @Test public void retryIsAllowedAfterFailure() {
        PreviewLoadState state = new PreviewLoadState();
        state.start("page-1");
        state.failed("page-1");

        assertTrue(state.start("page-1"));
        assertEquals(PreviewLoadState.Status.LOADING, state.getStatus());
    }

    @Test public void selectingAnotherPageClearsErrorAndIgnoresStaleResult() {
        PreviewLoadState state = new PreviewLoadState();
        state.start("page-1");
        state.failed("page-1");

        assertTrue(state.start("page-2"));
        assertEquals(PreviewLoadState.Status.LOADING, state.getStatus());
        assertFalse(state.loaded("page-1"));
        assertTrue(state.failed("page-2"));
        assertEquals(PreviewLoadState.Status.ERROR, state.getStatus());
    }
}
