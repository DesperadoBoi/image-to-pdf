package com.desperadoboi.imagetopdf.ui.idcard;

public final class IdCardExportState {
    public enum Phase {
        IDLE,
        RUNNING,
        SUCCEEDED,
        CANCELLED,
        ERROR
    }

    private final Phase phase;
    private final long operationId;
    private final int completedSteps;
    private final int totalSteps;
    private final boolean cancellationRequested;
    private final IdCardError error;

    private IdCardExportState(
            Phase phase,
            long operationId,
            int completedSteps,
            int totalSteps,
            boolean cancellationRequested,
            IdCardError error
    ) {
        this.phase = phase;
        this.operationId = operationId;
        this.completedSteps = completedSteps;
        this.totalSteps = totalSteps;
        this.cancellationRequested = cancellationRequested;
        this.error = error;
    }

    public static IdCardExportState idle() {
        return new IdCardExportState(Phase.IDLE, 0L, 0, 0, false, IdCardError.NONE);
    }

    public static IdCardExportState running(long operationId, int totalSteps) {
        return new IdCardExportState(
                Phase.RUNNING,
                operationId,
                0,
                totalSteps,
                false,
                IdCardError.NONE
        );
    }

    public Phase getPhase() {
        return phase;
    }

    public long getOperationId() {
        return operationId;
    }

    public int getCompletedSteps() {
        return completedSteps;
    }

    public int getTotalSteps() {
        return totalSteps;
    }

    public boolean isCancellationRequested() {
        return cancellationRequested;
    }

    public IdCardError getError() {
        return error;
    }

    public boolean isRunning() {
        return phase == Phase.RUNNING;
    }

    IdCardExportState withProgress(long id, int completed, int total) {
        if (!isRunning() || operationId != id) {
            return this;
        }
        return new IdCardExportState(
                phase,
                id,
                Math.max(0, Math.min(completed, total)),
                total,
                cancellationRequested,
                error
        );
    }

    IdCardExportState requestCancellation() {
        if (!isRunning() || cancellationRequested) {
            return this;
        }
        return new IdCardExportState(
                phase,
                operationId,
                completedSteps,
                totalSteps,
                true,
                error
        );
    }

    IdCardExportState finish(long id, Phase nextPhase, IdCardError nextError) {
        if (!isRunning() || operationId != id) {
            return this;
        }
        return new IdCardExportState(
                nextPhase,
                id,
                completedSteps,
                totalSteps,
                false,
                nextError
        );
    }
}
