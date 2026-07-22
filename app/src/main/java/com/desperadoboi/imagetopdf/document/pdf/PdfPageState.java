package com.desperadoboi.imagetopdf.document.pdf;

public final class PdfPageState {
    private int pageCount;
    private int currentPage;

    public PdfPageState(int pageCount, int restoredPage) {
        updatePageCount(pageCount);
        currentPage = clamp(restoredPage);
    }

    public void updatePageCount(int pageCount) {
        this.pageCount = Math.max(0, pageCount);
        currentPage = clamp(currentPage);
    }

    public int setCurrentPage(int requestedPage) {
        currentPage = clamp(requestedPage);
        return currentPage;
    }

    public int getPageCount() { return pageCount; }
    public int getCurrentPage() { return currentPage; }
    public boolean hasPrevious() { return currentPage > 0; }
    public boolean hasNext() { return pageCount > 0 && currentPage + 1 < pageCount; }

    private int clamp(int page) {
        if (pageCount <= 0) return 0;
        return Math.max(0, Math.min(page, pageCount - 1));
    }
}
