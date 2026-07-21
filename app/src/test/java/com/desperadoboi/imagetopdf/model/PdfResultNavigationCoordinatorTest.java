package com.desperadoboi.imagetopdf.model;

import android.net.FakeUri;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PdfResultNavigationCoordinatorTest {
    @Test
    public void successNavigatesToResult() {
        PdfResultNavigationCoordinator coordinator = coordinatorFor(1L);

        assertEquals(
                PdfResultNavigationCoordinator.Decision.NAVIGATE_TO_RESULT,
                coordinator.onGenerationStateChanged(success(1L))
        );
    }

    @Test
    public void cancelledStaysInEditor() {
        PdfResultNavigationCoordinator coordinator = coordinatorFor(1L);

        assertEquals(
                PdfResultNavigationCoordinator.Decision.STAY_IN_EDITOR,
                coordinator.onGenerationStateChanged(
                        PdfGenerationState.running(1L, 1).cancelled(1L)
                )
        );
    }

    @Test
    public void errorStaysInEditor() {
        PdfResultNavigationCoordinator coordinator = coordinatorFor(1L);

        assertEquals(
                PdfResultNavigationCoordinator.Decision.STAY_IN_EDITOR,
                coordinator.onGenerationStateChanged(
                        PdfGenerationState.running(1L, 1)
                                .failed(1L, new RuntimeException("failed"))
                )
        );
    }

    @Test
    public void staleOperationIsIgnored() {
        PdfResultNavigationCoordinator coordinator = coordinatorFor(2L);

        assertEquals(
                PdfResultNavigationCoordinator.Decision.IGNORE,
                coordinator.onGenerationStateChanged(success(1L))
        );
    }

    @Test
    public void duplicateSuccessDoesNotNavigateTwice() {
        PdfResultNavigationCoordinator coordinator = coordinatorFor(1L);

        coordinator.onGenerationStateChanged(success(1L));

        assertEquals(
                PdfResultNavigationCoordinator.Decision.IGNORE,
                coordinator.onGenerationStateChanged(success(1L))
        );
    }

    @Test
    public void newOperationCanNavigateAfterPreviousSuccess() {
        PdfResultNavigationCoordinator coordinator = coordinatorFor(1L);
        coordinator.onGenerationStateChanged(success(1L));

        coordinator.onOperationStarted(2L);

        assertEquals(
                PdfResultNavigationCoordinator.Decision.NAVIGATE_TO_RESULT,
                coordinator.onGenerationStateChanged(success(2L))
        );
    }

    private static PdfResultNavigationCoordinator coordinatorFor(long operationId) {
        PdfResultNavigationCoordinator coordinator = new PdfResultNavigationCoordinator();
        coordinator.onOperationStarted(operationId);
        return coordinator;
    }

    private static PdfGenerationState success(long operationId) {
        return PdfGenerationState.running(operationId, 1).succeeded(
                operationId,
                FakeUri.create("content://test/result-" + operationId + ".pdf")
        );
    }
}
