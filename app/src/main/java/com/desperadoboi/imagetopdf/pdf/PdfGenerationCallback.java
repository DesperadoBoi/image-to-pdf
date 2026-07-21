package com.desperadoboi.imagetopdf.pdf;

import android.net.Uri;

public interface PdfGenerationCallback {
    void onProgress(int completedPages, int totalPages);

    void onSuccess(Uri savedUri, long sizeBytes);

    void onCancelled();

    void onError(Exception exception);
}
