package com.desperadoboi.imagetopdf.model;

import android.net.Uri;

public final class PdfGenerationState {
    public enum Status {
        IDLE,
        RUNNING,
        SUCCESS,
        CANCELLED,
        ERROR
    }

    public static final long NO_OPERATION_ID = 0L;

    private final long operationId;
    private final Status status;
    private final int completedPages;
    private final int totalPages;
    private final boolean cancellationRequested;
    private final Uri savedUri;
    private final Exception error;

    private PdfGenerationState(
            long operationId,
            Status status,
            int completedPages,
            int totalPages,
            boolean cancellationRequested,
            Uri savedUri,
            Exception error
    ) {
        if (totalPages < 0) {
            throw new IllegalArgumentException("totalPages must be non-negative");
        }
        if (completedPages < 0 || completedPages > totalPages) {
            throw new IllegalArgumentException("completedPages must be within 0..totalPages");
        }
        if (status == Status.RUNNING && totalPages <= 0) {
            throw new IllegalArgumentException("totalPages must be positive while running");
        }
        this.operationId = operationId;
        this.status = status;
        this.completedPages = completedPages;
        this.totalPages = totalPages;
        this.cancellationRequested = cancellationRequested;
        this.savedUri = savedUri;
        this.error = error;
    }

    public static PdfGenerationState idle() {
        return new PdfGenerationState(
                NO_OPERATION_ID,
                Status.IDLE,
                0,
                0,
                false,
                null,
                null
        );
    }

    public static PdfGenerationState running(long operationId, int totalPages) {
        return new PdfGenerationState(
                operationId,
                Status.RUNNING,
                0,
                totalPages,
                false,
                null,
                null
        );
    }

    public PdfGenerationState withProgress(long operationId, int completedPages, int totalPages) {
        if (!matchesRunningOperation(operationId) || totalPages != this.totalPages) {
            return this;
        }
        int safeCompletedPages = Math.max(0, Math.min(completedPages, this.totalPages));
        return new PdfGenerationState(
                this.operationId,
                Status.RUNNING,
                safeCompletedPages,
                this.totalPages,
                cancellationRequested,
                null,
                null
        );
    }

    public PdfGenerationState withCancellationRequested(long operationId) {
        if (!matchesRunningOperation(operationId)) {
            return this;
        }
        return new PdfGenerationState(
                this.operationId,
                Status.RUNNING,
                completedPages,
                totalPages,
                true,
                null,
                null
        );
    }

    public PdfGenerationState cancelled(long operationId) {
        if (!matchesRunningOperation(operationId)) {
            return this;
        }
        return new PdfGenerationState(
                this.operationId,
                Status.CANCELLED,
                completedPages,
                totalPages,
                true,
                null,
                null
        );
    }

    public PdfGenerationState succeeded(long operationId, Uri savedUri) {
        if (!matchesRunningOperation(operationId)) {
            return this;
        }
        return new PdfGenerationState(
                this.operationId,
                Status.SUCCESS,
                totalPages,
                totalPages,
                false,
                savedUri,
                null
        );
    }

    public PdfGenerationState failed(long operationId, Exception error) {
        if (!matchesRunningOperation(operationId)) {
            return this;
        }
        return new PdfGenerationState(
                this.operationId,
                Status.ERROR,
                completedPages,
                totalPages,
                cancellationRequested,
                null,
                error
        );
    }

    public long getOperationId() {
        return operationId;
    }

    public Status getStatus() {
        return status;
    }

    public int getCompletedPages() {
        return completedPages;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public boolean isCancellationRequested() {
        return cancellationRequested;
    }

    public Uri getSavedUri() {
        return savedUri;
    }

    public Exception getError() {
        return error;
    }

    public boolean isRunning() {
        return status == Status.RUNNING;
    }

    public boolean isSucceeded() {
        return status == Status.SUCCESS;
    }

    public boolean isCancelled() {
        return status == Status.CANCELLED;
    }

    public boolean isError() {
        return status == Status.ERROR;
    }

    private boolean matchesRunningOperation(long operationId) {
        return status == Status.RUNNING && this.operationId == operationId;
    }
}
