package com.desperadoboi.imagetopdf.ui.smartscan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class ScanSessionState {
    private final String sessionId;
    private final List<ScanPage> pages;
    private final ScanPage pendingCapture;
    private final boolean captureInProgress;
    private final boolean finished;

    ScanSessionState(
            String sessionId,
            List<ScanPage> pages,
            ScanPage pendingCapture,
            boolean captureInProgress,
            boolean finished
    ) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId is required");
        this.pages = Collections.unmodifiableList(new ArrayList<>(pages));
        this.pendingCapture = pendingCapture;
        this.captureInProgress = captureInProgress;
        this.finished = finished;
    }

    public static ScanSessionState empty(String sessionId) {
        return new ScanSessionState(
                sessionId,
                Collections.emptyList(),
                null,
                false,
                false
        );
    }

    public String getSessionId() {
        return sessionId;
    }

    public List<ScanPage> getPages() {
        return pages;
    }

    public int getPageCount() {
        return pages.size();
    }

    public ScanPage getPendingCapture() {
        return pendingCapture;
    }

    public ScanPage getCurrentReviewPage() {
        return captureInProgress ? null : pendingCapture;
    }

    public boolean isCaptureInProgress() {
        return captureInProgress;
    }

    public boolean isFinished() {
        return finished;
    }

    public boolean hasPages() {
        return !pages.isEmpty();
    }
}
