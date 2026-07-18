package com.desperadoboi.imagetopdf.pdf;

import android.net.Uri;

public interface PdfGenerationCallback {
    void onSuccess(Uri savedUri);

    void onError(Exception exception);
}
