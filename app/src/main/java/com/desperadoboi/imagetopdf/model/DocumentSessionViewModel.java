package com.desperadoboi.imagetopdf.model;

import android.net.Uri;

import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;

public final class DocumentSessionViewModel extends ViewModel {
    private final ArrayList<PageItem> pages = new ArrayList<>();

    private PdfResult lastPdfResult;
    private String transientStatusMessage;
    private String pendingSuggestedFileName;
    private boolean generationInProgress;
    private boolean awaitingSaveLocation;

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
        generationInProgress = false;
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
        generationInProgress = false;
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
        return generationInProgress;
    }

    public void setGenerationInProgress(boolean generationInProgress) {
        this.generationInProgress = generationInProgress;
    }

    public boolean isAwaitingSaveLocation() {
        return awaitingSaveLocation;
    }

    public void setAwaitingSaveLocation(boolean awaitingSaveLocation) {
        this.awaitingSaveLocation = awaitingSaveLocation;
    }

    public boolean canEditPages() {
        return !generationInProgress && !awaitingSaveLocation;
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
}
