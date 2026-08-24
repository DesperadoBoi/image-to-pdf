package com.desperadoboi.imagetopdf.ui.watermark;

public final class PdfWatermarkExportState {
    public enum Phase {
        IDLE,
        GENERATING,
        AWAITING_DESTINATION,
        SAVING,
        SUCCEEDED,
        CANCELLED,
        ERROR
    }

    private final Phase phase;
    private final long operationId;
    private final int completedPages;
    private final int totalPages;
    private final boolean cancellationRequested;
    private final PdfWatermarkError error;
    private final String temporaryFileName;

    private PdfWatermarkExportState(
            Phase phase,
            long operationId,
            int completedPages,
            int totalPages,
            boolean cancellationRequested,
            PdfWatermarkError error,
            String temporaryFileName
    ) {
        this.phase = phase;
        this.operationId = operationId;
        this.completedPages = Math.max(0, completedPages);
        this.totalPages = Math.max(0, totalPages);
        this.cancellationRequested = cancellationRequested;
        this.error = error == null ? PdfWatermarkError.NONE : error;
        this.temporaryFileName = temporaryFileName;
    }

    public static PdfWatermarkExportState idle() {
        return new PdfWatermarkExportState(
                Phase.IDLE, 0L, 0, 0, false, PdfWatermarkError.NONE, null
        );
    }

    public static PdfWatermarkExportState generating(long operationId, int totalPages) {
        if (totalPages <= 0) throw new IllegalArgumentException("totalPages must be positive");
        return new PdfWatermarkExportState(
                Phase.GENERATING,
                operationId,
                0,
                totalPages,
                false,
                PdfWatermarkError.NONE,
                null
        );
    }

    public PdfWatermarkExportState withProgress(long id, int completed, int total) {
        if (phase != Phase.GENERATING || operationId != id || total != totalPages) return this;
        return new PdfWatermarkExportState(
                phase, operationId, Math.min(Math.max(0, completed), totalPages),
                totalPages, cancellationRequested, error, temporaryFileName
        );
    }

    public PdfWatermarkExportState awaitingDestination(long id, String fileName) {
        if (phase != Phase.GENERATING || operationId != id) return this;
        return new PdfWatermarkExportState(
                Phase.AWAITING_DESTINATION, id, totalPages, totalPages,
                false, PdfWatermarkError.NONE, fileName
        );
    }

    public PdfWatermarkExportState saving(long id) {
        if (phase != Phase.AWAITING_DESTINATION || operationId != id) return this;
        return new PdfWatermarkExportState(
                Phase.SAVING, id, totalPages, totalPages,
                false, PdfWatermarkError.NONE, temporaryFileName
        );
    }

    public PdfWatermarkExportState requestCancellation() {
        if (!isBusy() || cancellationRequested) return this;
        return new PdfWatermarkExportState(
                phase, operationId, completedPages, totalPages,
                true, error, temporaryFileName
        );
    }

    public PdfWatermarkExportState finish(
            long id,
            Phase nextPhase,
            PdfWatermarkError nextError
    ) {
        if (operationId != id || !isBusy()) return this;
        return new PdfWatermarkExportState(
                nextPhase, id, completedPages, totalPages,
                false, nextError, null
        );
    }

    public static PdfWatermarkExportState restoreAwaiting(
            long id,
            int totalPages,
            String temporaryFileName
    ) {
        return new PdfWatermarkExportState(
                Phase.AWAITING_DESTINATION, id, totalPages, totalPages,
                false, PdfWatermarkError.NONE, temporaryFileName
        );
    }

    public static PdfWatermarkExportState interrupted(long id, int totalPages) {
        return new PdfWatermarkExportState(
                Phase.ERROR, id, 0, totalPages,
                false, PdfWatermarkError.CANCELLED, null
        );
    }

    public static PdfWatermarkExportState terminal(
            Phase phase,
            long id,
            int totalPages,
            PdfWatermarkError error
    ) {
        if (phase != Phase.SUCCEEDED && phase != Phase.CANCELLED && phase != Phase.ERROR) {
            throw new IllegalArgumentException("Terminal phase is required");
        }
        return new PdfWatermarkExportState(
                phase,
                id,
                phase == Phase.SUCCEEDED ? totalPages : 0,
                totalPages,
                false,
                error,
                null
        );
    }

    public Phase getPhase() { return phase; }
    public long getOperationId() { return operationId; }
    public int getCompletedPages() { return completedPages; }
    public int getTotalPages() { return totalPages; }
    public boolean isCancellationRequested() { return cancellationRequested; }
    public PdfWatermarkError getError() { return error; }
    public String getTemporaryFileName() { return temporaryFileName; }

    public boolean isBusy() {
        return phase == Phase.GENERATING
                || phase == Phase.AWAITING_DESTINATION
                || phase == Phase.SAVING;
    }
}
