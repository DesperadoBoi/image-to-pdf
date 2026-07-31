package com.desperadoboi.imagetopdf.document;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import com.desperadoboi.imagetopdf.document.spreadsheet.XlsxParseException;
import com.desperadoboi.imagetopdf.document.word.WordParseException;

public final class IncomingDocumentLoader {
    private static final int SIGNATURE_BYTES = 8 * 1024;

    private final ContentResolver contentResolver;
    private final TemporaryDocumentStore temporaryDocumentStore;
    private final DocumentTypeResolver documentTypeResolver = new DocumentTypeResolver();

    public IncomingDocumentLoader(Context context, TemporaryDocumentStore temporaryDocumentStore) {
        contentResolver = context.getApplicationContext().getContentResolver();
        this.temporaryDocumentStore = temporaryDocumentStore;
    }

    public IncomingDocument load(
            Uri sourceUri,
            String storedDisplayName,
            String localizedFallbackName,
            AtomicBoolean cancelled
    )
            throws DocumentLoadException {
        Metadata metadata = queryMetadata(
                sourceUri,
                storedDisplayName,
                localizedFallbackName
        );
        String sourceMimeType;
        try {
            sourceMimeType = contentResolver.getType(sourceUri);
        } catch (SecurityException exception) {
            throw new DocumentLoadException(
                    DocumentLoadException.Reason.PERMISSION_LOST,
                    "Read permission is unavailable",
                    exception
            );
        } catch (RuntimeException ignored) {
            sourceMimeType = null;
        }
        if (metadata.sizeBytes >= 0L
                && metadata.sizeBytes > DocumentLimits.MAX_INCOMING_BYTES) {
            throw new DocumentLoadException(
                    isSpreadsheetHint(sourceMimeType, metadata.displayName)
                            ? DocumentLoadException.Reason.SPREADSHEET_TOO_LARGE
                            : DocumentLoadException.Reason.TOO_LARGE,
                    "Document exceeds the incoming file limit"
            );
        }

        File cachedFile;
        try {
            cachedFile = temporaryDocumentStore.copy(sourceUri, cancelled);
        } catch (DocumentLoadException exception) {
            if (exception.getReason() == DocumentLoadException.Reason.TOO_LARGE
                    && isSpreadsheetHint(sourceMimeType, metadata.displayName)) {
                throw new DocumentLoadException(
                        DocumentLoadException.Reason.SPREADSHEET_TOO_LARGE,
                        "Spreadsheet exceeds the incoming file limit",
                        exception
                );
            }
            throw exception;
        }
        long actualSize = cachedFile.length();
        try {
            byte[] prefix = readPrefix(cachedFile);
            DocumentType documentType = documentTypeResolver.resolve(
                    sourceMimeType,
                    prefix,
                    cachedFile,
                    metadata.displayName
            );
            if (!DocumentLimits.isAllowedKnownSize(actualSize, documentType)) {
                throw new DocumentLoadException(
                        documentType == DocumentType.XLSX
                                ? DocumentLoadException.Reason.SPREADSHEET_TOO_LARGE
                                : documentType == DocumentType.DOCX
                                        ? DocumentLoadException.Reason.DOCX_TOO_LARGE
                                        : DocumentLoadException.Reason.TOO_LARGE,
                        "Document exceeds the safe limit for its type"
                );
            }
            return new IncomingDocument(
                    sourceUri,
                    metadata.displayName,
                    sourceMimeType,
                    actualSize,
                    documentType,
                    cachedFile
            );
        } catch (DocumentLoadException exception) {
            temporaryDocumentStore.delete(cachedFile);
            throw exception;
        } catch (XlsxParseException exception) {
            temporaryDocumentStore.delete(cachedFile);
            throw new DocumentLoadException(
                    mapSpreadsheetReason(exception.getReason()),
                    "Unable to inspect XLSX package",
                    exception
            );
        } catch (OoxmlPackageDetector.DetectionException exception) {
            temporaryDocumentStore.delete(cachedFile);
            throw new DocumentLoadException(
                    mapDetectionReason(
                            exception,
                            sourceMimeType,
                            metadata.displayName
                    ),
                    "Unable to inspect OOXML package",
                    exception
            );
        } catch (WordParseException exception) {
            temporaryDocumentStore.delete(cachedFile);
            throw new DocumentLoadException(
                    mapWordReason(exception.getReason()),
                    "Unable to inspect DOCX package",
                    exception
            );
        } catch (IOException | RuntimeException exception) {
            temporaryDocumentStore.delete(cachedFile);
            throw new DocumentLoadException(
                    DocumentLoadException.Reason.CORRUPTED,
                    "Unable to inspect document",
                    exception
            );
        }
    }

    private DocumentLoadException.Reason mapDetectionReason(
            OoxmlPackageDetector.DetectionException exception,
            String mimeType,
            String displayName
    ) {
        boolean spreadsheet = exception.getFamily()
                == OoxmlPackageDetector.Family.SPREADSHEET
                || isSpreadsheetHint(mimeType, displayName);
        boolean word = exception.getFamily() == OoxmlPackageDetector.Family.WORD
                || isWordHint(mimeType, displayName);
        if (spreadsheet) {
            switch (exception.getReason()) {
                case TOO_LARGE:
                    return DocumentLoadException.Reason.SPREADSHEET_TOO_LARGE;
                case ENCRYPTED:
                    return DocumentLoadException.Reason.SPREADSHEET_ENCRYPTED;
                case CORRUPTED:
                default:
                    return DocumentLoadException.Reason.SPREADSHEET_CORRUPTED;
            }
        }
        if (word) {
            switch (exception.getReason()) {
                case TOO_LARGE:
                    return DocumentLoadException.Reason.DOCX_TOO_LARGE;
                case ENCRYPTED:
                    return DocumentLoadException.Reason.DOCX_ENCRYPTED;
                case CORRUPTED:
                default:
                    return DocumentLoadException.Reason.DOCX_CORRUPTED;
            }
        }
        return exception.getReason() == OoxmlPackageDetector.Reason.TOO_LARGE
                ? DocumentLoadException.Reason.TOO_LARGE
                : DocumentLoadException.Reason.CORRUPTED;
    }

    private DocumentLoadException.Reason mapSpreadsheetReason(
            XlsxParseException.Reason reason
    ) {
        switch (reason) {
            case TOO_LARGE:
                return DocumentLoadException.Reason.SPREADSHEET_TOO_LARGE;
            case ENCRYPTED:
                return DocumentLoadException.Reason.SPREADSHEET_ENCRYPTED;
            case ACTIVE_CONTENT:
                return DocumentLoadException.Reason.SPREADSHEET_ACTIVE_CONTENT;
            case UNSUPPORTED:
            case CORRUPTED:
            default:
                return DocumentLoadException.Reason.SPREADSHEET_CORRUPTED;
        }
    }

    private DocumentLoadException.Reason mapWordReason(WordParseException.Reason reason) {
        switch (reason) {
            case TOO_LARGE:
                return DocumentLoadException.Reason.DOCX_TOO_LARGE;
            case ENCRYPTED:
                return DocumentLoadException.Reason.DOCX_ENCRYPTED;
            case UNSUPPORTED:
                return DocumentLoadException.Reason.DOCX_UNSUPPORTED;
            case CANCELLED:
                return DocumentLoadException.Reason.CANCELLED;
            case CORRUPTED:
            default:
                return DocumentLoadException.Reason.DOCX_CORRUPTED;
        }
    }

    private boolean isSpreadsheetHint(String mimeType, String displayName) {
        DocumentType mime = documentTypeResolver.fromMimeType(mimeType);
        if (mime == DocumentType.XLSX || mime == DocumentType.XLSM) return true;
        String name = SafeDisplayName.sanitize(displayName).toLowerCase(Locale.ROOT);
        return name.endsWith(".xlsx") || name.endsWith(".xlsm");
    }

    private boolean isWordHint(String mimeType, String displayName) {
        if (documentTypeResolver.fromMimeType(mimeType) == DocumentType.DOCX) return true;
        return SafeDisplayName.sanitize(displayName)
                .toLowerCase(Locale.ROOT)
                .endsWith(".docx");
    }

    private Metadata queryMetadata(
            Uri uri,
            String storedDisplayName,
            String localizedFallbackName
    ) throws DocumentLoadException {
        String fallbackName = SafeDisplayName.resolve(
                null,
                storedDisplayName,
                uri.getLastPathSegment(),
                localizedFallbackName
        );
        try (Cursor cursor = contentResolver.query(
                uri,
                new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE},
                null,
                null,
                null
        )) {
            if (cursor == null || !cursor.moveToFirst()) {
                return new Metadata(fallbackName, -1L);
            }
            String providerDisplayName = null;
            long size = -1L;
            int nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            if (nameColumn >= 0 && !cursor.isNull(nameColumn)) {
                providerDisplayName = cursor.getString(nameColumn);
            }
            int sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE);
            if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) {
                size = cursor.getLong(sizeColumn);
            }
            return new Metadata(
                    SafeDisplayName.resolve(
                            providerDisplayName,
                            storedDisplayName,
                            uri.getLastPathSegment(),
                            localizedFallbackName
                    ),
                    size
            );
        } catch (SecurityException exception) {
            throw new DocumentLoadException(
                    DocumentLoadException.Reason.PERMISSION_LOST,
                    "Read permission is unavailable",
                    exception
            );
        } catch (RuntimeException ignored) {
            return new Metadata(fallbackName, -1L);
        }
    }

    private byte[] readPrefix(File file) throws IOException {
        int requested = (int) Math.min(file.length(), SIGNATURE_BYTES);
        byte[] bytes = new byte[requested];
        try (FileInputStream inputStream = new FileInputStream(file)) {
            int offset = 0;
            while (offset < requested) {
                int read = inputStream.read(bytes, offset, requested - offset);
                if (read < 0) {
                    break;
                }
                offset += read;
            }
            if (offset == requested) {
                return bytes;
            }
            byte[] shortened = new byte[offset];
            System.arraycopy(bytes, 0, shortened, 0, offset);
            return shortened;
        }
    }

    private static final class Metadata {
        private final String displayName;
        private final long sizeBytes;

        private Metadata(String displayName, long sizeBytes) {
            this.displayName = displayName;
            this.sizeBytes = sizeBytes;
        }
    }
}
