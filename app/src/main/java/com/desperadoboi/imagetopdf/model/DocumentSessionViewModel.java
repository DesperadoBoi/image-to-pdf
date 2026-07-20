package com.desperadoboi.imagetopdf.model;

import android.net.Uri;

import androidx.lifecycle.ViewModel;

import com.desperadoboi.imagetopdf.pdf.CancellationToken;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DocumentSessionViewModel extends ViewModel {
    private final ArrayList<PageItem> pages = new ArrayList<>();
    private final ArrayList<WeakReference<PdfGenerationStateObserver>> pdfGenerationStateObservers =
            new ArrayList<>();
    private final ExecutorService pdfExecutor = Executors.newSingleThreadExecutor();

    private PdfGenerationState pdfGenerationState = PdfGenerationState.idle();
    private PdfResult lastPdfResult;
    private String transientStatusMessage;
    private String pendingSuggestedFileName;
    private boolean awaitingSaveLocation;
    private long nextGenerationOperationId = PdfGenerationState.NO_OPERATION_ID + 1L;
    private CancellationToken activeCancellationToken;

    public List<PageItem> getPages() {
        return pages;
    }

    public List<PageItem> getPagesSnapshot() {
        return new ArrayList<>(pages);
    }

    public boolean hasPages() {
        return !pages.isEmpty();
    }

    public int getPageCount() {
        return pages.size();
    }

    public void replacePages(List<Uri> imageUris) {
        pages.clear();
        appendPageItems(imageUris);
        lastPdfResult = null;
        transientStatusMessage = null;
        pendingSuggestedFileName = null;
        cancelActiveGeneration();
        setPdfGenerationState(PdfGenerationState.idle());
        awaitingSaveLocation = false;
    }

    public int appendPages(List<Uri> imageUris) {
        int firstInsertedPosition = pages.size();
        appendPageItems(imageUris);
        transientStatusMessage = null;
        return firstInsertedPosition;
    }

    public PageItem rotatePage(int position) {
        validatePosition(position);
        PageItem rotatedPage = pages.get(position).rotateClockwise();
        pages.set(position, rotatedPage);
        transientStatusMessage = null;
        return rotatedPage;
    }

    public PageItem deletePage(int position) {
        validatePosition(position);
        transientStatusMessage = null;
        return pages.remove(position);
    }

    public boolean movePage(int fromPosition, int toPosition) {
        if (fromPosition < 0
                || fromPosition >= pages.size()
                || toPosition < 0
                || toPosition >= pages.size()) {
            return false;
        }
        boolean moved = PageOrderManager.move(pages, fromPosition, toPosition);
        if (moved) {
            transientStatusMessage = null;
        }
        return moved;
    }

    public void clearForNewDocument() {
        pages.clear();
        lastPdfResult = null;
        transientStatusMessage = null;
        pendingSuggestedFileName = null;
        cancelActiveGeneration();
        setPdfGenerationState(PdfGenerationState.idle());
        awaitingSaveLocation = false;
    }

    public PdfResult getLastPdfResult() {
        return lastPdfResult;
    }

    public void setLastPdfResult(PdfResult lastPdfResult) {
        this.lastPdfResult = lastPdfResult;
    }

    public void clearLastPdfResult() {
        lastPdfResult = null;
    }

    public String getTransientStatusMessage() {
        return transientStatusMessage;
    }

    public void setTransientStatusMessage(String transientStatusMessage) {
        this.transientStatusMessage = transientStatusMessage;
    }

    public String getPendingSuggestedFileName() {
        return pendingSuggestedFileName;
    }

    public void setPendingSuggestedFileName(String pendingSuggestedFileName) {
        this.pendingSuggestedFileName = pendingSuggestedFileName;
    }

    public boolean isGenerationInProgress() {
        return getPdfGenerationState().isRunning();
    }

    public void addPdfGenerationStateObserver(PdfGenerationStateObserver observer) {
        if (observer == null) {
            return;
        }
        pdfGenerationStateObservers.add(new WeakReference<>(observer));
        observer.onPdfGenerationStateChanged(pdfGenerationState);
    }

    public void removePdfGenerationStateObserver(PdfGenerationStateObserver observer) {
        if (observer == null) {
            return;
        }
        Iterator<WeakReference<PdfGenerationStateObserver>> iterator =
                pdfGenerationStateObservers.iterator();
        while (iterator.hasNext()) {
            PdfGenerationStateObserver existingObserver = iterator.next().get();
            if (existingObserver == null || existingObserver == observer) {
                iterator.remove();
            }
        }
    }

    public PdfGenerationState getPdfGenerationState() {
        return pdfGenerationState;
    }

    public Executor getPdfExecutor() {
        return pdfExecutor;
    }

    public GenerationOperation startGeneration(int totalPages) {
        if (isGenerationInProgress()) {
            return null;
        }
        if (totalPages <= 0) {
            throw new IllegalArgumentException("totalPages must be positive");
        }

        long operationId = nextGenerationOperationId++;
        activeCancellationToken = new CancellationToken();
        transientStatusMessage = null;
        setPdfGenerationState(PdfGenerationState.running(operationId, totalPages));
        return new GenerationOperation(operationId, activeCancellationToken);
    }

    public void requestCancelGeneration() {
        PdfGenerationState state = getPdfGenerationState();
        if (!state.isRunning()) {
            return;
        }
        if (activeCancellationToken != null) {
            activeCancellationToken.cancel();
        }
        setPdfGenerationState(state.withCancellationRequested(state.getOperationId()));
    }

    public void updateGenerationProgress(long operationId, int completedPages, int totalPages) {
        setPdfGenerationState(
                pdfGenerationState.withProgress(operationId, completedPages, totalPages)
        );
    }

    public void completeGenerationSuccess(
            long operationId,
            Uri savedUri,
            String fallbackDisplayName,
            int pageCount
    ) {
        PdfGenerationState currentState = getPdfGenerationState();
        PdfGenerationState nextState = currentState.succeeded(operationId, savedUri);
        if (!nextState.isSucceeded() || nextState.getOperationId() != operationId) {
            setPdfGenerationState(nextState);
            return;
        }
        lastPdfResult = new PdfResult(
                savedUri,
                fallbackDisplayName,
                PdfResult.UNKNOWN_SIZE_BYTES,
                pageCount
        );
        clearActiveGeneration(operationId);
        pendingSuggestedFileName = null;
        setPdfGenerationState(nextState);
    }

    public void completeGenerationCancelled(long operationId) {
        PdfGenerationState currentState = getPdfGenerationState();
        PdfGenerationState nextState = currentState.cancelled(operationId);
        if (!nextState.isCancelled() || nextState.getOperationId() != operationId) {
            setPdfGenerationState(nextState);
            return;
        }
        clearActiveGeneration(operationId);
        pendingSuggestedFileName = null;
        setPdfGenerationState(nextState);
    }

    public void completeGenerationError(long operationId, Exception exception) {
        PdfGenerationState currentState = getPdfGenerationState();
        PdfGenerationState nextState = currentState.failed(operationId, exception);
        if (!nextState.isError() || nextState.getOperationId() != operationId) {
            setPdfGenerationState(nextState);
            return;
        }
        clearActiveGeneration(operationId);
        pendingSuggestedFileName = null;
        setPdfGenerationState(nextState);
    }

    public boolean isAwaitingSaveLocation() {
        return awaitingSaveLocation;
    }

    public void setAwaitingSaveLocation(boolean awaitingSaveLocation) {
        this.awaitingSaveLocation = awaitingSaveLocation;
    }

    public boolean canEditPages() {
        return !isGenerationInProgress() && !awaitingSaveLocation;
    }

    @Override
    protected void onCleared() {
        cancelActiveGeneration();
        pdfExecutor.shutdown();
        super.onCleared();
    }

    private void appendPageItems(List<Uri> imageUris) {
        if (imageUris == null || imageUris.isEmpty()) {
            return;
        }
        for (Uri imageUri : imageUris) {
            pages.add(new PageItem(imageUri));
        }
    }

    private void validatePosition(int position) {
        if (position < 0 || position >= pages.size()) {
            throw new IndexOutOfBoundsException("position is out of bounds: " + position);
        }
    }

    private void cancelActiveGeneration() {
        if (activeCancellationToken != null) {
            activeCancellationToken.cancel();
            activeCancellationToken = null;
        }
    }

    private void clearActiveGeneration(long operationId) {
        PdfGenerationState state = getPdfGenerationState();
        if (state.getOperationId() == operationId) {
            activeCancellationToken = null;
        }
    }

    private void setPdfGenerationState(PdfGenerationState state) {
        pdfGenerationState = state;
        notifyPdfGenerationStateObservers(state);
    }

    private void notifyPdfGenerationStateObservers(PdfGenerationState state) {
        Iterator<WeakReference<PdfGenerationStateObserver>> iterator =
                pdfGenerationStateObservers.iterator();
        while (iterator.hasNext()) {
            PdfGenerationStateObserver observer = iterator.next().get();
            if (observer == null) {
                iterator.remove();
                continue;
            }
            observer.onPdfGenerationStateChanged(state);
        }
    }

    public interface PdfGenerationStateObserver {
        void onPdfGenerationStateChanged(PdfGenerationState state);
    }

    public static final class GenerationOperation {
        private final long operationId;
        private final CancellationToken cancellationToken;

        private GenerationOperation(long operationId, CancellationToken cancellationToken) {
            this.operationId = operationId;
            this.cancellationToken = cancellationToken;
        }

        public long getOperationId() {
            return operationId;
        }

        public CancellationToken getCancellationToken() {
            return cancellationToken;
        }
    }
}
