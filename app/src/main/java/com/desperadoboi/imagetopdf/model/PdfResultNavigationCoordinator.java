package com.desperadoboi.imagetopdf.model;

import java.util.Objects;

public final class PdfResultNavigationCoordinator {
    public enum Decision {
        NAVIGATE_TO_RESULT,
        STAY_IN_EDITOR,
        IGNORE
    }

    private long activeOperationId = PdfGenerationState.NO_OPERATION_ID;
    private long navigatedOperationId = PdfGenerationState.NO_OPERATION_ID;

    public void onOperationStarted(long operationId) {
        if (operationId <= PdfGenerationState.NO_OPERATION_ID) {
            throw new IllegalArgumentException("operationId must be positive");
        }
        activeOperationId = operationId;
    }

    public Decision onGenerationStateChanged(PdfGenerationState state) {
        Objects.requireNonNull(state, "state is required");
        if (state.getOperationId() != activeOperationId) {
            return Decision.IGNORE;
        }
        if (state.isSucceeded()) {
            if (navigatedOperationId == activeOperationId) {
                return Decision.IGNORE;
            }
            navigatedOperationId = activeOperationId;
            return Decision.NAVIGATE_TO_RESULT;
        }
        if (state.isCancelled() || state.isError()) {
            return Decision.STAY_IN_EDITOR;
        }
        return Decision.IGNORE;
    }

    public void reset() {
        activeOperationId = PdfGenerationState.NO_OPERATION_ID;
        navigatedOperationId = PdfGenerationState.NO_OPERATION_ID;
    }
}
