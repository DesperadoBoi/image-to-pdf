package com.desperadoboi.imagetopdf.pdf;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public final class PdfPreviewLoader {
    private final ContentResolver contentResolver;
    private final Executor mainExecutor;
    private final ExecutorService decodeExecutor = Executors.newSingleThreadExecutor();
    private final AtomicLong nextRequestId = new AtomicLong(1L);

    private volatile long activeRequestId;
    private volatile boolean closed;

    public PdfPreviewLoader(ContentResolver contentResolver, Executor mainExecutor) {
        this.contentResolver = Objects.requireNonNull(
                contentResolver,
                "contentResolver is required"
        );
        this.mainExecutor = Objects.requireNonNull(mainExecutor, "mainExecutor is required");
    }

    public void load(Uri uri, int maxWidth, int maxHeight, Callback callback) {
        Objects.requireNonNull(uri, "uri is required");
        Objects.requireNonNull(callback, "callback is required");
        if (maxWidth <= 0 || maxHeight <= 0) {
            throw new IllegalArgumentException("Preview bounds must be positive");
        }
        long requestId = nextRequestId.getAndIncrement();
        activeRequestId = requestId;
        decodeExecutor.execute(() -> render(requestId, uri, maxWidth, maxHeight, callback));
    }

    public void clear() {
        activeRequestId = nextRequestId.getAndIncrement();
    }

    public void shutdown() {
        closed = true;
        clear();
        decodeExecutor.shutdownNow();
    }

    private void render(
            long requestId,
            Uri uri,
            int maxWidth,
            int maxHeight,
            Callback callback
    ) {
        Bitmap bitmap = null;
        Exception failure = null;
        try (ParcelFileDescriptor descriptor = contentResolver.openFileDescriptor(uri, "r")) {
            if (descriptor == null) {
                throw new IOException("Provider returned no file descriptor");
            }
            try (PdfRenderer renderer = new PdfRenderer(descriptor)) {
                if (renderer.getPageCount() <= 0) {
                    throw new IOException("PDF has no pages");
                }
                try (PdfRenderer.Page page = renderer.openPage(0)) {
                    float scale = Math.min(
                            (float) maxWidth / page.getWidth(),
                            (float) maxHeight / page.getHeight()
                    );
                    int width = Math.max(1, Math.round(page.getWidth() * scale));
                    int height = Math.max(1, Math.round(page.getHeight() * scale));
                    bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    bitmap.eraseColor(Color.WHITE);
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                }
            }
        } catch (Exception exception) {
            failure = exception;
            recycle(bitmap);
            bitmap = null;
        }

        Bitmap renderedBitmap = bitmap;
        Exception renderFailure = failure;
        mainExecutor.execute(() -> deliver(requestId, renderedBitmap, renderFailure, callback));
    }

    private void deliver(
            long requestId,
            Bitmap bitmap,
            Exception failure,
            Callback callback
    ) {
        if (closed || activeRequestId != requestId) {
            recycle(bitmap);
            return;
        }
        if (bitmap != null) {
            callback.onLoaded(bitmap);
        } else {
            callback.onError(failure == null
                    ? new IOException("Unable to render PDF preview")
                    : failure);
        }
    }

    private void recycle(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }

    public interface Callback {
        void onLoaded(Bitmap bitmap);

        void onError(Exception exception);
    }
}
