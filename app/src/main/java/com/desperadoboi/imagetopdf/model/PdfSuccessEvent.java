package com.desperadoboi.imagetopdf.model;

import java.util.Objects;

public final class PdfSuccessEvent {
    private final long operationId;
    private final PdfResult result;
    private boolean consumed;

    public PdfSuccessEvent(long operationId, PdfResult result) {
        if (operationId <= PdfGenerationState.NO_OPERATION_ID) {
            throw new IllegalArgumentException("operationId must be positive");
        }
        this.operationId = operationId;
        this.result = Objects.requireNonNull(result, "result is required");
    }

    public long getOperationId() {
        return operationId;
    }

    public PdfResult getResult() {
        return result;
    }

    public synchronized boolean isConsumed() {
        return consumed;
    }

    public synchronized boolean consume() {
        if (consumed) {
            return false;
        }
        consumed = true;
        return true;
    }

    public boolean matches(PdfResult candidate) {
        if (candidate == null) {
            return false;
        }
        return result == candidate
                || (result.getUri() == candidate.getUri()
                        && result.getTimestamp() == candidate.getTimestamp()
                        && result.getPageCount() == candidate.getPageCount());
    }
}
