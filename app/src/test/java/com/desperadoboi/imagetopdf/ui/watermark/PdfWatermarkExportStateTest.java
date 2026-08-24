package com.desperadoboi.imagetopdf.ui.watermark;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class PdfWatermarkExportStateTest {
    @Test public void operationMovesThroughGenerateSaveAndSuccess() {
        PdfWatermarkExportState state = PdfWatermarkExportState.generating(7L, 4)
                .withProgress(7L, 2, 4)
                .awaitingDestination(7L, "watermark_pdf_550e8400-e29b-41d4-a716-446655440000.tmp")
                .saving(7L)
                .finish(7L, PdfWatermarkExportState.Phase.SUCCEEDED,
                        PdfWatermarkError.NONE);

        assertEquals(PdfWatermarkExportState.Phase.SUCCEEDED, state.getPhase());
        assertFalse(state.isBusy());
        assertEquals(4, state.getTotalPages());
    }

    @Test public void staleProgressCannotChangeCurrentOperation() {
        PdfWatermarkExportState state = PdfWatermarkExportState.generating(8L, 3);
        assertTrue(state == state.withProgress(7L, 3, 3));
    }

    @Test public void cancellationRequestKeepsOperationIdentity() {
        PdfWatermarkExportState state = PdfWatermarkExportState.generating(9L, 8)
                .requestCancellation();
        assertTrue(state.isBusy());
        assertTrue(state.isCancellationRequested());
        assertEquals(9L, state.getOperationId());
    }

    @Test public void interruptedWorkRestoresAsTerminalCancellation() {
        PdfWatermarkExportState state = PdfWatermarkExportState.interrupted(3L, 12);
        assertEquals(PdfWatermarkExportState.Phase.ERROR, state.getPhase());
        assertEquals(PdfWatermarkError.CANCELLED, state.getError());
        assertFalse(state.isBusy());
    }
}
