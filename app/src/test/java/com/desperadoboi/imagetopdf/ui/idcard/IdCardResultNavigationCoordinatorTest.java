package com.desperadoboi.imagetopdf.ui.idcard;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class IdCardResultNavigationCoordinatorTest {
    @Test public void completedExportNavigatesExactlyOnce() {
        IdCardResultNavigationCoordinator coordinator =
                new IdCardResultNavigationCoordinator();
        coordinator.onOperationStarted(7L);
        coordinator.onOperationSucceeded(7L);

        assertTrue(coordinator.consumePendingNavigation());
        assertFalse(coordinator.consumePendingNavigation());
    }

    @Test public void staleCompletionCannotNavigateAReplacementOperation() {
        IdCardResultNavigationCoordinator coordinator =
                new IdCardResultNavigationCoordinator();
        coordinator.onOperationStarted(7L);
        coordinator.onOperationStarted(8L);
        coordinator.onOperationSucceeded(7L);

        assertFalse(coordinator.consumePendingNavigation());
        coordinator.onOperationSucceeded(8L);
        assertTrue(coordinator.consumePendingNavigation());
    }

    @Test public void resetDropsAResultThatBelongsToTheClosedSession() {
        IdCardResultNavigationCoordinator coordinator =
                new IdCardResultNavigationCoordinator();
        coordinator.onOperationStarted(7L);
        coordinator.onOperationSucceeded(7L);
        coordinator.reset();

        assertFalse(coordinator.consumePendingNavigation());
    }
}
