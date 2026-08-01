package com.desperadoboi.imagetopdf.ui.idcard;

public final class IdCardResultNavigationCoordinator {
    private static final long NO_OPERATION_ID = 0L;

    private long activeOperationId = NO_OPERATION_ID;
    private long succeededOperationId = NO_OPERATION_ID;
    private long navigatedOperationId = NO_OPERATION_ID;

    public void onOperationStarted(long operationId) {
        if (operationId <= NO_OPERATION_ID) {
            throw new IllegalArgumentException("operationId must be positive");
        }
        activeOperationId = operationId;
        succeededOperationId = NO_OPERATION_ID;
    }

    public void onOperationSucceeded(long operationId) {
        if (operationId == activeOperationId) {
            succeededOperationId = operationId;
        }
    }

    public boolean consumePendingNavigation() {
        if (succeededOperationId == NO_OPERATION_ID
                || succeededOperationId != activeOperationId
                || navigatedOperationId == succeededOperationId) {
            return false;
        }
        navigatedOperationId = succeededOperationId;
        return true;
    }

    public void reset() {
        activeOperationId = NO_OPERATION_ID;
        succeededOperationId = NO_OPERATION_ID;
        navigatedOperationId = NO_OPERATION_ID;
    }
}
