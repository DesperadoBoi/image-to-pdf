package com.desperadoboi.imagetopdf.document.pdf;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public final class PdfDocumentRenderer {
    private static final int MAX_RENDER_EDGE = 4096;
    private static final long MAX_RENDER_PIXELS = 8_000_000L;

    private final Executor callbackExecutor;
    private final ExecutorService renderExecutor = Executors.newSingleThreadExecutor();
    private final AtomicLong requestIds = new AtomicLong();
    private final PageLruCache<Bitmap> cache = new PageLruCache<>(
            2,
            PdfDocumentRenderer::recycle
    );

    private volatile boolean closed;
    private volatile long activeRequestId;
    private ParcelFileDescriptor descriptor;
    private PdfRenderer renderer;

    public PdfDocumentRenderer(Executor callbackExecutor) {
        this.callbackExecutor = callbackExecutor;
    }

    public void open(File file, OpenCallback callback) {
        renderExecutor.execute(() -> {
            try {
                descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
                renderer = new PdfRenderer(descriptor);
                int pageCount = renderer.getPageCount();
                callbackExecutor.execute(() -> {
                    if (!closed) callback.onOpened(pageCount);
                });
            } catch (Exception exception) {
                closeResources();
                callbackExecutor.execute(() -> {
                    if (!closed) callback.onError(exception);
                });
            }
        });
    }

    public void renderPage(int pageIndex, int targetWidth, int targetHeight, RenderCallback callback) {
        Bitmap cached = cache.get(pageIndex);
        if (cached != null && !cached.isRecycled()) {
            callback.onRendered(pageIndex, cached);
            return;
        }
        long requestId = requestIds.incrementAndGet();
        activeRequestId = requestId;
        renderExecutor.execute(() -> renderInternal(
                requestId,
                pageIndex,
                targetWidth,
                targetHeight,
                callback
        ));
    }

    public void close() {
        closed = true;
        activeRequestId = requestIds.incrementAndGet();
        renderExecutor.execute(() -> {
            cache.clear();
            closeResources();
        });
        renderExecutor.shutdown();
    }

    private void renderInternal(
            long requestId,
            int pageIndex,
            int targetWidth,
            int targetHeight,
            RenderCallback callback
    ) {
        Bitmap bitmap = null;
        Exception failure = null;
        try {
            if (renderer == null || pageIndex < 0 || pageIndex >= renderer.getPageCount()) {
                throw new IOException("PDF page is unavailable");
            }
            try (PdfRenderer.Page page = renderer.openPage(pageIndex)) {
                float scale = Math.min(
                        Math.max(1, targetWidth) / (float) page.getWidth(),
                        MAX_RENDER_EDGE / (float) Math.max(page.getWidth(), page.getHeight())
                );
                if (targetHeight > 0) {
                    scale = Math.min(
                            Math.max(scale, Math.min(
                                    targetHeight / (float) page.getHeight(),
                                    2f
                            )),
                            MAX_RENDER_EDGE / (float) Math.max(page.getWidth(), page.getHeight())
                    );
                }
                int width = Math.max(1, Math.round(page.getWidth() * scale));
                int height = Math.max(1, Math.round(page.getHeight() * scale));
                long pixels = (long) width * height;
                if (pixels > MAX_RENDER_PIXELS) {
                    float reduction = (float) Math.sqrt(MAX_RENDER_PIXELS / (double) pixels);
                    width = Math.max(1, Math.round(width * reduction));
                    height = Math.max(1, Math.round(height * reduction));
                }
                bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                bitmap.eraseColor(Color.WHITE);
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            }
        } catch (Exception exception) {
            failure = exception;
            recycle(bitmap);
            bitmap = null;
        } catch (OutOfMemoryError error) {
            failure = new IOException("Not enough memory to render PDF page", error);
            recycle(bitmap);
            bitmap = null;
        }
        Bitmap result = bitmap;
        Exception resultFailure = failure;
        callbackExecutor.execute(() -> {
            if (closed || requestId != activeRequestId) {
                recycle(result);
            } else if (result != null) {
                cache.put(pageIndex, result);
                callback.onRendered(pageIndex, result);
            } else {
                callback.onError(resultFailure);
            }
        });
    }

    private void closeResources() {
        if (renderer != null) {
            renderer.close();
            renderer = null;
        }
        if (descriptor != null) {
            try {
                descriptor.close();
            } catch (IOException ignored) {
                // Best-effort cleanup of an app-owned cache descriptor.
            }
            descriptor = null;
        }
    }

    private static void recycle(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
    }

    public interface OpenCallback {
        void onOpened(int pageCount);
        void onError(Exception exception);
    }

    public interface RenderCallback {
        void onRendered(int pageIndex, Bitmap bitmap);
        void onError(Exception exception);
    }
}
