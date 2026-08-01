package com.desperadoboi.imagetopdf.ui.idcard;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class IdCardExportStateTest {
    @Test public void runningExportTracksOneOrTwoSides() {
        IdCardExportState state = IdCardExportState.running(7L, 2);

        assertTrue(state.isRunning());
        assertEquals(2, state.getTotalSteps());
        assertEquals(0, state.getCompletedSteps());
    }

    @Test public void progressIsClampedToTotal() {
        IdCardExportState state = IdCardExportState.running(7L, 2)
                .withProgress(7L, 5, 2);

        assertEquals(2, state.getCompletedSteps());
    }

    @Test public void staleProgressCannotMutateCurrentExport() {
        IdCardExportState state = IdCardExportState.running(8L, 1);

        assertSame(state, state.withProgress(7L, 1, 1));
    }

    @Test public void cancellationRequestIsIdempotent() {
        IdCardExportState requested = IdCardExportState.running(9L, 1)
                .requestCancellation();

        assertTrue(requested.isCancellationRequested());
        assertSame(requested, requested.requestCancellation());
    }

    @Test public void cancellationFinishesOnlyMatchingOperation() {
        IdCardExportState running = IdCardExportState.running(9L, 1);

        assertSame(
                running,
                running.finish(8L, IdCardExportState.Phase.CANCELLED,
                        IdCardError.OPERATION_CANCELLED)
        );
        IdCardExportState cancelled = running.finish(
                9L,
                IdCardExportState.Phase.CANCELLED,
                IdCardError.OPERATION_CANCELLED
        );
        assertFalse(cancelled.isRunning());
        assertEquals(IdCardExportState.Phase.CANCELLED, cancelled.getPhase());
    }
}
