package com.desperadoboi.imagetopdf.model;

import android.net.Uri;

import androidx.lifecycle.ViewModel;

import com.desperadoboi.imagetopdf.pdf.CancellationToken;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.UnaryOperator;

public final class DocumentSessionViewModel extends ViewModel {
    private final ArrayList<PageItem> pages = new ArrayList<>();
    private final ArrayList<WeakReference<PdfGenerationStateObserver>> pdfGenerationStateObservers =
            new ArrayList<>();
    private final ExecutorService pdfExecutor = Executors.newSingleThreadExecutor();

    private PdfGenerationState pdfGenerationState = PdfGenerationState.idle();
    private PdfResult lastPdfResult;
    private String transientStatusMessage;
    private String pendingSuggestedFileName;
    private PendingCapturedImage pendingCapturedImage;
    private boolean awaitingSaveLocation;
    private int pendingEditorScrollPosition = ImageImportResult.NO_POSITION;
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

    public List<String> replacePages(List<Uri> imageUris) {
        return importImages(
                new ImageImportRequest(
                        ImageImportSource.GALLERY,
                        ImageImportMode.NEW_DOCUMENT
                ),
                imageUris
        ).getCapturedFileNamesToDelete();
    }

    public List<String> replacePagesWithCameraCapture(Uri imageUri, String capturedFileName) {
        return importCameraImage(
                new ImageImportRequest(
                        ImageImportSource.CAMERA,
                        ImageImportMode.NEW_DOCUMENT
                ),
                imageUri,
                capturedFileName
        ).getCapturedFileNamesToDelete();
    }

    public int appendPages(List<Uri> imageUris) {
        ImageImportResult result = importImages(
                new ImageImportRequest(
                        ImageImportSource.GALLERY,
                        ImageImportMode.APPEND_TO_DOCUMENT
                ),
                imageUris
        );
        return result.hasChanges() ? result.getFirstInsertedPosition() : pages.size();
    }

    public int appendCameraPage(Uri imageUri, String capturedFileName) {
        return importCameraImage(
                new ImageImportRequest(
                        ImageImportSource.CAMERA,
                        ImageImportMode.APPEND_TO_DOCUMENT
                ),
                imageUri,
                capturedFileName
        ).getFirstInsertedPosition();
    }

    public ImageImportResult importImages(ImageImportRequest request, List<Uri> imageUris) {
        Objects.requireNonNull(request, "request is required");
        if (request.getSource() == ImageImportSource.CAMERA) {
            throw new IllegalArgumentException("Camera import requires captured file metadata");
        }
        if (imageUris == null || imageUris.isEmpty()) {
            return ImageImportResult.noChange();
        }

        ArrayList<PageItem> importedPages = new ArrayList<>(imageUris.size());
        for (Uri imageUri : imageUris) {
            importedPages.add(createExternalPage(request.getSource(), imageUri));
        }
        return applyImportedPages(request, importedPages);
    }

    public ImageImportResult importImages(
            ImageImportMode mode,
            List<ImageImportEntry> importEntries
    ) {
        Objects.requireNonNull(mode, "mode is required");
        if (importEntries == null || importEntries.isEmpty()) {
            return ImageImportResult.noChange();
        }
        ArrayList<PageItem> importedPages = new ArrayList<>(importEntries.size());
        for (ImageImportEntry entry : importEntries) {
            Objects.requireNonNull(entry, "import entry is required");
            if (entry.getSource() == ImageImportSource.CAMERA) {
                importedPages.add(PageItem.camera(
                        entry.getUri(),
                        entry.getCapturedFileName()
                ));
            } else {
                importedPages.add(createExternalPage(entry.getSource(), entry.getUri()));
            }
        }
        return applyImportedPages(
                new ImageImportRequest(ImageImportSource.IN_APP_GALLERY, mode),
                importedPages
        );
    }

    public ImageImportResult importCameraImage(
            ImageImportRequest request,
            Uri imageUri,
            String capturedFileName
    ) {
        Objects.requireNonNull(request, "request is required");
        if (request.getSource() != ImageImportSource.CAMERA) {
            throw new IllegalArgumentException("Camera metadata requires CAMERA source");
        }
        return applyImportedPages(
                request,
                java.util.Collections.singletonList(PageItem.camera(imageUri, capturedFileName))
        );
    }

    public PageItem rotatePage(int position) {
        validatePosition(position);
        PageItem rotatedPage = pages.get(position).rotateClockwise();
        pages.set(position, rotatedPage);
        markDocumentEdited();
        return rotatedPage;
    }

    public PageItem rotatePageLeft(long pageId) {
        return updatePage(pageId, PageItem::rotateCounterClockwise);
    }

    public PageItem rotatePageRight(long pageId) {
        return updatePage(pageId, PageItem::rotateClockwise);
    }

    public PageItem updatePageCrop(long pageId, CropRect cropRect) {
        Objects.requireNonNull(cropRect, "cropRect is required");
        return updatePage(pageId, pageItem -> pageItem.withCropRect(cropRect));
    }

    public PageItem updatePagePerspective(long pageId, PerspectiveQuad perspectiveQuad) {
        Objects.requireNonNull(perspectiveQuad, "perspectiveQuad is required");
        return updatePage(pageId, pageItem -> pageItem.withPerspectiveQuad(perspectiveQuad));
    }

    public PageItem resetPageCrop(long pageId) {
        return updatePage(pageId, PageItem::resetCrop);
    }

    public PageItem resetPagePerspective(long pageId) {
        return updatePage(pageId, PageItem::resetPerspective);
    }

    public PageItem resetPageEdits(long pageId) {
        return updatePage(pageId, PageItem::resetEdits);
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

    public List<String> clearForNewDocument() {
        List<String> capturedFileNames = collectCurrentCapturedFileNames();
        pages.clear();
        lastPdfResult = null;
        transientStatusMessage = null;
        pendingSuggestedFileName = null;
        pendingCapturedImage = null;
        pendingEditorScrollPosition = ImageImportResult.NO_POSITION;
        cancelActiveGeneration();
        setPdfGenerationState(PdfGenerationState.idle());
        awaitingSaveLocation = false;
        return capturedFileNames;
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

    public PendingCapturedImage getPendingCapturedImage() {
        return pendingCapturedImage;
    }

    public void setPendingCapturedImage(
            Uri uri,
            String capturedFileName,
            CaptureTarget target
    ) {
        pendingCapturedImage = new PendingCapturedImage(uri, capturedFileName, target);
    }

    public PendingCapturedImage clearPendingCapturedImage() {
        PendingCapturedImage capturedImage = pendingCapturedImage;
        pendingCapturedImage = null;
        return capturedImage;
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

    public void setPendingEditorScrollPosition(int position) {
        pendingEditorScrollPosition = position;
    }

    public int consumePendingEditorScrollPosition() {
        int position = pendingEditorScrollPosition;
        pendingEditorScrollPosition = ImageImportResult.NO_POSITION;
        return position;
    }

    @Override
    protected void onCleared() {
        cancelActiveGeneration();
        pdfExecutor.shutdown();
        super.onCleared();
    }

    private PageItem createExternalPage(ImageImportSource source, Uri imageUri) {
        if (source == ImageImportSource.GALLERY || source == ImageImportSource.IN_APP_GALLERY) {
            return PageItem.gallery(imageUri);
        }
        if (source == ImageImportSource.FILES) {
            return PageItem.files(imageUri);
        }
        throw new IllegalArgumentException("Unsupported external image source: " + source);
    }

    private ImageImportResult applyImportedPages(
            ImageImportRequest request,
            List<PageItem> importedPages
    ) {
        if (importedPages.isEmpty()) {
            return ImageImportResult.noChange();
        }

        int firstInsertedPosition;
        List<String> capturedFileNamesToDelete;
        if (request.getMode() == ImageImportMode.NEW_DOCUMENT) {
            capturedFileNamesToDelete = collectCurrentCapturedFileNames();
            for (PageItem importedPage : importedPages) {
                if (importedPage.isAppOwnedCapture()) {
                    capturedFileNamesToDelete.remove(importedPage.getCapturedFileName());
                }
            }
            pages.clear();
            firstInsertedPosition = 0;
            pages.addAll(importedPages);
            clearDocumentStateForReplacement();
        } else {
            capturedFileNamesToDelete = java.util.Collections.emptyList();
            firstInsertedPosition = pages.size();
            pages.addAll(importedPages);
            transientStatusMessage = null;
            resetFinishedGenerationState();
        }
        return new ImageImportResult(
                firstInsertedPosition,
                importedPages.size(),
                capturedFileNamesToDelete
        );
    }

    private List<String> collectCurrentCapturedFileNames() {
        List<String> capturedFileNames = CapturedPageCleanup.collectCapturedFileNames(pages);
        if (pendingCapturedImage != null) {
            capturedFileNames.add(pendingCapturedImage.getCapturedFileName());
        }
        return capturedFileNames;
    }

    private void clearDocumentStateForReplacement() {
        lastPdfResult = null;
        transientStatusMessage = null;
        pendingSuggestedFileName = null;
        pendingCapturedImage = null;
        pendingEditorScrollPosition = ImageImportResult.NO_POSITION;
        cancelActiveGeneration();
        setPdfGenerationState(PdfGenerationState.idle());
        awaitingSaveLocation = false;
    }

    private void resetFinishedGenerationState() {
        if (!pdfGenerationState.isRunning()) {
            setPdfGenerationState(PdfGenerationState.idle());
        }
    }

    private void validatePosition(int position) {
        if (position < 0 || position >= pages.size()) {
            throw new IndexOutOfBoundsException("position is out of bounds: " + position);
        }
    }

    private PageItem updatePage(long pageId, UnaryOperator<PageItem> update) {
        int position = findPagePosition(pageId);
        if (position < 0) {
            throw new IllegalArgumentException("Unknown page ID: " + pageId);
        }
        PageItem currentPage = pages.get(position);
        PageItem updatedPage = Objects.requireNonNull(
                update.apply(currentPage),
                "Updated page is required"
        );
        if (updatedPage != currentPage) {
            pages.set(position, updatedPage);
            markDocumentEdited();
        }
        return updatedPage;
    }

    private int findPagePosition(long pageId) {
        for (int position = 0; position < pages.size(); position++) {
            if (pages.get(position).getId() == pageId) {
                return position;
            }
        }
        return -1;
    }

    private void markDocumentEdited() {
        transientStatusMessage = null;
        lastPdfResult = null;
        resetFinishedGenerationState();
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

    public enum CaptureTarget {
        HOME,
        EDITOR,
        PICKER
    }

    public static final class PendingCapturedImage {
        private final Uri uri;
        private final String capturedFileName;
        private final CaptureTarget target;

        private PendingCapturedImage(Uri uri, String capturedFileName, CaptureTarget target) {
            this.uri = Objects.requireNonNull(uri, "uri is required");
            if (capturedFileName == null || capturedFileName.trim().isEmpty()) {
                throw new IllegalArgumentException("capturedFileName is required");
            }
            this.capturedFileName = capturedFileName.trim();
            this.target = Objects.requireNonNull(target, "target is required");
        }

        public Uri getUri() {
            return uri;
        }

        public String getCapturedFileName() {
            return capturedFileName;
        }

        public CaptureTarget getTarget() {
            return target;
        }
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
