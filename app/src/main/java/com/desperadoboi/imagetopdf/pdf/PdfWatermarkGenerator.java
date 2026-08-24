package com.desperadoboi.imagetopdf.pdf;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.pdf.PdfDocument;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import com.desperadoboi.imagetopdf.ui.watermark.PdfWatermarkOptions;
import com.desperadoboi.imagetopdf.ui.watermark.WatermarkPainter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;

public final class PdfWatermarkGenerator {
    public static final int TARGET_DPI = 180;
    public static final long MAX_BITMAP_PIXELS = 8_000_000L;
    public static final int MAX_BITMAP_EDGE = 6000;
    public static final int MAX_PAGE_DIMENSION_POINTS = 14_400;
    public static final long DEFAULT_TEMP_TTL_MS = 24L * 60L * 60L * 1000L;

    private static final String TEMP_DIRECTORY = "pdf_watermark";
    private static final String TEMP_PREFIX = "watermark_pdf_";
    private static final String TEMP_SUFFIX = ".tmp";
    private static final int COPY_BUFFER_BYTES = 32 * 1024;

    private final ContentResolver contentResolver;
    private final File temporaryDirectory;
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final WatermarkPainter watermarkPainter = new WatermarkPainter();

    public PdfWatermarkGenerator(Context context) {
        Context applicationContext = Objects.requireNonNull(context).getApplicationContext();
        contentResolver = applicationContext.getContentResolver();
        temporaryDirectory = new File(applicationContext.getCacheDir(), TEMP_DIRECTORY);
    }

    public void generateToTemporaryFile(
            File sourceFile,
            int expectedPageCount,
            PdfWatermarkOptions options,
            Set<Integer> selectedPages,
            CancellationToken cancellationToken,
            Executor backgroundExecutor,
            Executor callbackExecutor,
            GenerationCallback callback
    ) {
        Objects.requireNonNull(backgroundExecutor);
        Objects.requireNonNull(callbackExecutor);
        Objects.requireNonNull(callback);
        backgroundExecutor.execute(() -> {
            File temporaryFile = null;
            try {
                temporaryFile = createTemporaryFile();
                writePdf(
                        sourceFile,
                        expectedPageCount,
                        options,
                        selectedPages,
                        temporaryFile,
                        cancellationToken,
                        callbackExecutor,
                        callback
                );
                throwIfCancelled(cancellationToken);
                File completedFile = temporaryFile;
                callbackExecutor.execute(() -> {
                    if (cancellationToken == null || cancellationToken.isCancelled()) {
                        deleteTemporaryFile(completedFile);
                        callback.onCancelled();
                    } else {
                        callback.onGenerated(completedFile.getName(), completedFile.length());
                    }
                });
            } catch (PdfGenerationCancelledException exception) {
                deleteTemporaryFile(temporaryFile);
                callbackExecutor.execute(callback::onCancelled);
            } catch (OutOfMemoryError error) {
                deleteTemporaryFile(temporaryFile);
                GenerationException exception = new GenerationException(
                        Reason.OUT_OF_MEMORY,
                        "Insufficient memory for PDF watermark",
                        error
                );
                callbackExecutor.execute(() -> callback.onError(exception));
            } catch (Exception exception) {
                deleteTemporaryFile(temporaryFile);
                Exception result = exception instanceof GenerationException
                        ? exception
                        : new GenerationException(
                                Reason.CREATE_PDF,
                                "Unable to create watermarked PDF",
                                exception
                        );
                callbackExecutor.execute(() -> callback.onError(result));
            }
        });
    }

    public void saveTemporaryFile(
            String temporaryFileName,
            Uri outputUri,
            CancellationToken cancellationToken,
            Executor backgroundExecutor,
            Executor callbackExecutor,
            SaveCallback callback
    ) {
        backgroundExecutor.execute(() -> {
            File temporaryFile = resolveTemporaryFile(temporaryFileName);
            try {
                if (temporaryFile == null) {
                    throw new GenerationException(
                            Reason.SOURCE_UNAVAILABLE,
                            "Temporary PDF is unavailable"
                    );
                }
                long size = copyToOutput(temporaryFile, outputUri, cancellationToken);
                callbackExecutor.execute(() -> {
                    if (cancellationToken == null || cancellationToken.isCancelled()) {
                        deletePartialOutput(outputUri);
                        callback.onCancelled();
                    } else {
                        callback.onSaved(outputUri, size);
                    }
                });
            } catch (PdfGenerationCancelledException exception) {
                deletePartialOutput(outputUri);
                callbackExecutor.execute(callback::onCancelled);
            } catch (Exception exception) {
                deletePartialOutput(outputUri);
                Exception result = exception instanceof GenerationException
                        ? exception
                        : new GenerationException(
                                Reason.SAVE_PDF,
                                "Unable to save watermarked PDF",
                                exception
                        );
                callbackExecutor.execute(() -> callback.onError(result));
            } finally {
                deleteTemporaryFile(temporaryFile);
            }
        });
    }

    private void writePdf(
            File sourceFile,
            int expectedPageCount,
            PdfWatermarkOptions options,
            Set<Integer> selectedPages,
            File outputFile,
            CancellationToken cancellationToken,
            Executor callbackExecutor,
            GenerationCallback callback
    ) throws IOException, PdfGenerationCancelledException {
        if (sourceFile == null || !sourceFile.isFile()) {
            throw new GenerationException(Reason.SOURCE_UNAVAILABLE, "Source PDF is unavailable");
        }
        if (expectedPageCount <= 0 || options == null || !options.hasValidText()) {
            throw new IllegalArgumentException("Valid PDF watermark request is required");
        }
        Set<Integer> targets = selectedPages == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new HashSet<>(selectedPages));
        if (targets.isEmpty()) throw new IllegalArgumentException("Selected pages are required");

        notifyProgress(callbackExecutor, callback, 0, expectedPageCount);
        try (ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(
                sourceFile,
                ParcelFileDescriptor.MODE_READ_ONLY
        ); PdfRenderer renderer = new PdfRenderer(descriptor);
             OutputStream output = new FileOutputStream(outputFile, false)) {
            if (renderer.getPageCount() != expectedPageCount) {
                throw new GenerationException(
                        Reason.SOURCE_UNAVAILABLE,
                        "Source PDF changed during export"
                );
            }
            PdfDocument document = new PdfDocument();
            try {
                for (int index = 0; index < expectedPageCount; index++) {
                    throwIfCancelled(cancellationToken);
                    renderPage(
                            renderer,
                            document,
                            index,
                            targets.contains(index),
                            options,
                            cancellationToken
                    );
                    notifyProgress(callbackExecutor, callback, index + 1, expectedPageCount);
                }
                throwIfCancelled(cancellationToken);
                document.writeTo(output);
                output.flush();
                throwIfCancelled(cancellationToken);
            } finally {
                document.close();
            }
        } catch (SecurityException exception) {
            throw new GenerationException(Reason.SOURCE_UNAVAILABLE, "Source PDF is protected", exception);
        } catch (GenerationException | PdfGenerationCancelledException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new GenerationException(Reason.CREATE_PDF, "Unable to render source PDF", exception);
        }
    }

    private void renderPage(
            PdfRenderer renderer,
            PdfDocument document,
            int pageIndex,
            boolean applyWatermark,
            PdfWatermarkOptions options,
            CancellationToken cancellationToken
    ) throws IOException, PdfGenerationCancelledException {
        Bitmap bitmap = null;
        try (PdfRenderer.Page sourcePage = renderer.openPage(pageIndex)) {
            int pageWidth = sourcePage.getWidth();
            int pageHeight = sourcePage.getHeight();
            validatePageDimensions(pageWidth, pageHeight);
            RenderSize renderSize = calculateRenderSize(pageWidth, pageHeight);
            bitmap = Bitmap.createBitmap(
                    renderSize.width,
                    renderSize.height,
                    Bitmap.Config.ARGB_8888
            );
            bitmap.eraseColor(Color.WHITE);
            sourcePage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT);
            throwIfCancelled(cancellationToken);

            PdfDocument.Page outputPage = document.startPage(
                    new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
            );
            boolean finished = false;
            try {
                Canvas canvas = outputPage.getCanvas();
                canvas.drawColor(Color.WHITE);
                canvas.drawBitmap(
                        bitmap,
                        new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight()),
                        new RectF(0f, 0f, pageWidth, pageHeight),
                        bitmapPaint
                );
                if (applyWatermark) {
                    watermarkPainter.draw(canvas, pageWidth, pageHeight, options);
                }
                document.finishPage(outputPage);
                finished = true;
            } finally {
                if (!finished) {
                    try {
                        document.finishPage(outputPage);
                    } catch (RuntimeException ignored) {
                        // The incomplete temporary PDF is removed by the caller.
                    }
                }
            }
        } finally {
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        }
    }

    public static RenderSize calculateRenderSize(int pageWidthPoints, int pageHeightPoints) {
        if (pageWidthPoints <= 0 || pageHeightPoints <= 0) {
            throw new IllegalArgumentException("PDF page dimensions must be positive");
        }
        float scale = TARGET_DPI / 72f;
        scale = Math.min(
                scale,
                MAX_BITMAP_EDGE / (float) Math.max(pageWidthPoints, pageHeightPoints)
        );
        int width = Math.max(1, Math.round(pageWidthPoints * scale));
        int height = Math.max(1, Math.round(pageHeightPoints * scale));
        long pixels = (long) width * height;
        if (pixels > MAX_BITMAP_PIXELS) {
            float reduction = (float) Math.sqrt(MAX_BITMAP_PIXELS / (double) pixels);
            width = Math.max(1, Math.round(width * reduction));
            height = Math.max(1, Math.round(height * reduction));
        }
        return new RenderSize(width, height);
    }

    private void validatePageDimensions(int width, int height) throws GenerationException {
        if (width <= 0 || height <= 0
                || width > MAX_PAGE_DIMENSION_POINTS
                || height > MAX_PAGE_DIMENSION_POINTS) {
            throw new GenerationException(Reason.TOO_LARGE, "PDF page dimensions are unsafe");
        }
    }

    private long copyToOutput(
            File temporaryFile,
            Uri outputUri,
            CancellationToken cancellationToken
    ) throws IOException, PdfGenerationCancelledException {
        if (outputUri == null) throw new GenerationException(Reason.SAVE_PDF, "Output is required");
        try (InputStream input = new FileInputStream(temporaryFile);
             CountingOutputStream output = new CountingOutputStream(openOutput(outputUri))) {
            byte[] buffer = new byte[COPY_BUFFER_BYTES];
            int read;
            while ((read = input.read(buffer)) != -1) {
                throwIfCancelled(cancellationToken);
                output.write(buffer, 0, read);
            }
            output.flush();
            throwIfCancelled(cancellationToken);
            return output.count;
        }
    }

    private OutputStream openOutput(Uri uri) throws IOException {
        try {
            OutputStream output = contentResolver.openOutputStream(uri, "wt");
            if (output == null) throw new IOException("Provider returned no output stream");
            return output;
        } catch (IOException | RuntimeException exception) {
            throw new GenerationException(Reason.SAVE_PDF, "Unable to open PDF output", exception);
        }
    }

    private File createTemporaryFile() throws IOException {
        if (!temporaryDirectory.isDirectory()
                && !temporaryDirectory.mkdirs()
                && !temporaryDirectory.isDirectory()) {
            throw new IOException("Unable to create watermark PDF cache");
        }
        return new File(
                temporaryDirectory,
                TEMP_PREFIX + UUID.randomUUID() + TEMP_SUFFIX
        );
    }

    public File resolveTemporaryFile(String fileName) {
        if (!isGeneratedTemporaryName(fileName)) return null;
        File candidate = new File(temporaryDirectory, fileName);
        try {
            if (!candidate.getCanonicalFile().getParentFile()
                    .equals(temporaryDirectory.getCanonicalFile())) {
                return null;
            }
        } catch (IOException exception) {
            return null;
        }
        return candidate.isFile() ? candidate : null;
    }

    public void deleteTemporaryFile(String fileName) {
        deleteTemporaryFile(resolveTemporaryFile(fileName));
    }

    private void deleteTemporaryFile(File file) {
        if (file != null && file.isFile()) {
            try {
                file.delete();
            } catch (SecurityException ignored) {
                // TTL cleanup handles a rare file retained by the platform.
            }
        }
    }

    public int cleanupExpiredTemporaryFiles(long nowMillis, long ttlMillis) {
        if (ttlMillis < 0L) return 0;
        File[] files = temporaryDirectory.listFiles();
        if (files == null) return 0;
        long cutoff = nowMillis - ttlMillis;
        int removed = 0;
        for (File file : files) {
            if (file.isFile() && isGeneratedTemporaryName(file.getName())
                    && file.lastModified() < cutoff && file.delete()) {
                removed++;
            }
        }
        return removed;
    }

    public static boolean isGeneratedTemporaryName(String value) {
        if (value == null || !value.startsWith(TEMP_PREFIX) || !value.endsWith(TEMP_SUFFIX)) {
            return false;
        }
        String uuid = value.substring(TEMP_PREFIX.length(), value.length() - TEMP_SUFFIX.length());
        try {
            UUID.fromString(uuid);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private void deletePartialOutput(Uri uri) {
        if (uri == null) return;
        try {
            contentResolver.delete(uri, null, null);
        } catch (RuntimeException ignored) {
            // Some SAF providers cannot delete; writes use truncate mode.
        }
    }

    private void notifyProgress(
            Executor executor,
            GenerationCallback callback,
            int completed,
            int total
    ) {
        executor.execute(() -> callback.onProgress(
                Math.max(0, Math.min(completed, total)),
                total
        ));
    }

    private void throwIfCancelled(CancellationToken token)
            throws PdfGenerationCancelledException {
        if (token == null || token.isCancelled() || Thread.currentThread().isInterrupted()) {
            throw new PdfGenerationCancelledException();
        }
    }

    public interface GenerationCallback {
        void onProgress(int completedPages, int totalPages);
        void onGenerated(String temporaryFileName, long sizeBytes);
        void onCancelled();
        void onError(Exception exception);
    }

    public interface SaveCallback {
        void onSaved(Uri outputUri, long sizeBytes);
        void onCancelled();
        void onError(Exception exception);
    }

    public enum Reason {
        SOURCE_UNAVAILABLE,
        TOO_LARGE,
        OUT_OF_MEMORY,
        CREATE_PDF,
        SAVE_PDF
    }

    public static final class GenerationException extends IOException {
        private final Reason reason;

        public GenerationException(Reason reason, String message) {
            super(message);
            this.reason = reason;
        }

        public GenerationException(Reason reason, String message, Throwable cause) {
            super(message, cause);
            this.reason = reason;
        }

        public Reason getReason() { return reason; }
    }

    public static final class RenderSize {
        private final int width;
        private final int height;

        private RenderSize(int width, int height) {
            this.width = width;
            this.height = height;
        }

        public int getWidth() { return width; }
        public int getHeight() { return height; }
        public long getPixels() { return (long) width * height; }
    }

    private static final class CountingOutputStream extends FilterOutputStream {
        private long count;

        private CountingOutputStream(OutputStream outputStream) {
            super(outputStream);
        }

        @Override
        public void write(int value) throws IOException {
            out.write(value);
            count++;
        }

        @Override
        public void write(byte[] values, int offset, int length) throws IOException {
            out.write(values, offset, length);
            count += length;
        }
    }
}
