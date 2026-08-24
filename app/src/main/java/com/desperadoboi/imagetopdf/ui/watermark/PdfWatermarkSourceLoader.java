package com.desperadoboi.imagetopdf.ui.watermark;

import android.content.Context;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import com.desperadoboi.imagetopdf.document.DocumentLoadException;
import com.desperadoboi.imagetopdf.document.DocumentType;
import com.desperadoboi.imagetopdf.document.IncomingDocument;
import com.desperadoboi.imagetopdf.document.IncomingDocumentLoader;
import com.desperadoboi.imagetopdf.document.TemporaryDocumentStore;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PdfWatermarkSourceLoader {
    public static final int MAX_PAGE_COUNT = 1000;

    private final TemporaryDocumentStore store;
    private final IncomingDocumentLoader loader;

    public PdfWatermarkSourceLoader(Context context, TemporaryDocumentStore store) {
        Context applicationContext = Objects.requireNonNull(context).getApplicationContext();
        this.store = Objects.requireNonNull(store);
        loader = new IncomingDocumentLoader(applicationContext, store);
    }

    public Result load(
            Uri uri,
            String storedDisplayName,
            String localizedFallback,
            AtomicBoolean cancelled
    ) throws SourceLoadException {
        IncomingDocument document;
        try {
            document = loader.load(uri, storedDisplayName, localizedFallback, cancelled);
        } catch (DocumentLoadException exception) {
            throw new SourceLoadException(mapLoadError(exception), exception);
        } catch (OutOfMemoryError error) {
            throw new SourceLoadException(PdfWatermarkError.OUT_OF_MEMORY, error);
        }
        File cachedFile = document.getCachedFile();
        try {
            if (document.getDocumentType() != DocumentType.PDF) {
                throw new SourceLoadException(PdfWatermarkError.CORRUPTED_PDF, null);
            }
            int pageCount = inspect(cachedFile);
            return new Result(
                    cachedFile.getName(),
                    document.getDisplayName(),
                    pageCount
            );
        } catch (SourceLoadException exception) {
            store.delete(cachedFile);
            throw exception;
        }
    }

    private int inspect(File file) throws SourceLoadException {
        try {
            if (PdfEncryptionDetector.hasEncryptionDictionary(file)) {
                throw new SourceLoadException(PdfWatermarkError.ENCRYPTED_PDF, null);
            }
        } catch (SourceLoadException exception) {
            throw exception;
        } catch (IOException | SecurityException exception) {
            throw new SourceLoadException(PdfWatermarkError.OPEN_PDF, exception);
        }
        try (ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(
                file,
                ParcelFileDescriptor.MODE_READ_ONLY
        ); PdfRenderer renderer = new PdfRenderer(descriptor)) {
            int pageCount = renderer.getPageCount();
            if (pageCount <= 0) {
                throw new SourceLoadException(PdfWatermarkError.EMPTY_PDF, null);
            }
            if (pageCount > MAX_PAGE_COUNT) {
                throw new SourceLoadException(PdfWatermarkError.TOO_LARGE, null);
            }
            try (PdfRenderer.Page ignored = renderer.openPage(0)) {
                return pageCount;
            }
        } catch (SourceLoadException exception) {
            throw exception;
        } catch (SecurityException exception) {
            throw new SourceLoadException(PdfWatermarkError.ENCRYPTED_PDF, exception);
        } catch (IOException | RuntimeException exception) {
            throw new SourceLoadException(PdfWatermarkError.CORRUPTED_PDF, exception);
        } catch (OutOfMemoryError error) {
            throw new SourceLoadException(PdfWatermarkError.OUT_OF_MEMORY, error);
        }
    }

    private PdfWatermarkError mapLoadError(DocumentLoadException exception) {
        switch (exception.getReason()) {
            case TOO_LARGE:
                return PdfWatermarkError.TOO_LARGE;
            case ENCRYPTED:
                return PdfWatermarkError.ENCRYPTED_PDF;
            case CORRUPTED:
            case UNSUPPORTED:
                return PdfWatermarkError.CORRUPTED_PDF;
            case CANCELLED:
                return PdfWatermarkError.CANCELLED;
            case PERMISSION_LOST:
            case PROVIDER_UNREADABLE:
            default:
                return PdfWatermarkError.OPEN_PDF;
        }
    }

    public static final class Result {
        private final String cacheFileName;
        private final String displayName;
        private final int pageCount;

        private Result(String cacheFileName, String displayName, int pageCount) {
            this.cacheFileName = cacheFileName;
            this.displayName = displayName;
            this.pageCount = pageCount;
        }

        public String getCacheFileName() { return cacheFileName; }
        public String getDisplayName() { return displayName; }
        public int getPageCount() { return pageCount; }
    }

    public static final class SourceLoadException extends IOException {
        private final PdfWatermarkError error;

        private SourceLoadException(PdfWatermarkError error, Throwable cause) {
            super("Unable to load PDF source", cause);
            this.error = error;
        }

        public PdfWatermarkError getError() { return error; }
    }
}
