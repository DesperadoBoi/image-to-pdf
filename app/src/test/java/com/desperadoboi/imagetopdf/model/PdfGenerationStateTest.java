package com.desperadoboi.imagetopdf.model;

import android.net.FakeUri;
import android.net.Uri;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class PdfGenerationStateTest {
    @Test
    public void idleStateHasNoOperationOrPages() {
        PdfGenerationState state = PdfGenerationState.idle();

        assertEquals(PdfGenerationState.NO_OPERATION_ID, state.getOperationId());
        assertEquals(0, state.getCompletedPages());
        assertEquals(0, state.getTotalPages());
        assertFalse(state.isRunning());
    }

    @Test
    public void runningStateStartsWithZeroCompletedPages() {
        PdfGenerationState state = PdfGenerationState.running(1L, 3);

        assertTrue(state.isRunning());
        assertEquals(0, state.getCompletedPages());
        assertEquals(3, state.getTotalPages());
    }

    @Test
    public void progressCanIncreaseSequentially() {
        PdfGenerationState state = PdfGenerationState.running(1L, 3)
                .withProgress(1L, 1, 3)
                .withProgress(1L, 2, 3)
                .withProgress(1L, 3, 3);

        assertEquals(3, state.getCompletedPages());
        assertEquals(3, state.getTotalPages());
    }

    @Test
    public void progressCannotExceedTotal() {
        PdfGenerationState state = PdfGenerationState.running(1L, 3)
                .withProgress(1L, 4, 3);

        assertEquals(3, state.getCompletedPages());
    }

    @Test
    public void invalidRunningTotalIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PdfGenerationState.running(1L, 0)
        );
    }

    @Test
    public void cancellationMovesStateToCancelled() {
        PdfGenerationState state = PdfGenerationState.running(1L, 3)
                .withCancellationRequested(1L)
                .cancelled(1L);

        assertTrue(state.isCancelled());
        assertTrue(state.isCancellationRequested());
        assertFalse(state.isSucceeded());
    }

    @Test
    public void lateSuccessAfterCancellationRequestStillReachesTerminalState() {
        Uri savedUri = FakeUri.create("content://test/result.pdf");
        PdfGenerationState state = PdfGenerationState.running(1L, 3)
                .withCancellationRequested(1L)
                .succeeded(1L, savedUri);

        assertTrue(state.isSucceeded());
        assertFalse(state.isCancellationRequested());
        assertFalse(state.isCancelled());
    }

    @Test
    public void successDoesNotOverrideError() {
        Uri savedUri = FakeUri.create("content://test/result.pdf");
        Exception error = new IllegalStateException("failed");
        PdfGenerationState state = PdfGenerationState.running(1L, 3)
                .failed(1L, error)
                .succeeded(1L, savedUri);

        assertTrue(state.isError());
        assertFalse(state.isSucceeded());
        assertSame(error, state.getError());
    }

    @Test
    public void successCompletesAllPages() {
        Uri savedUri = FakeUri.create("content://test/result.pdf");
        PdfGenerationState state = PdfGenerationState.running(1L, 3)
                .withProgress(1L, 2, 3)
                .succeeded(1L, savedUri);

        assertTrue(state.isSucceeded());
        assertEquals(3, state.getCompletedPages());
        assertEquals(3, state.getTotalPages());
        assertSame(savedUri, state.getSavedUri());
    }

    @Test
    public void oldOperationProgressDoesNotChangeCurrentState() {
        PdfGenerationState state = PdfGenerationState.running(2L, 3);

        PdfGenerationState unchangedState = state.withProgress(1L, 2, 3);

        assertSame(state, unchangedState);
        assertEquals(0, unchangedState.getCompletedPages());
        assertEquals(2L, unchangedState.getOperationId());
    }
}
