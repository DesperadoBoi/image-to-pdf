package com.desperadoboi.imagetopdf.document;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

public final class IncomingDocumentLoader {
    private static final int SIGNATURE_BYTES = 8 * 1024;

    private final ContentResolver contentResolver;
    private final TemporaryDocumentStore temporaryDocumentStore;
    private final DocumentTypeResolver documentTypeResolver = new DocumentTypeResolver();

    public IncomingDocumentLoader(Context context, TemporaryDocumentStore temporaryDocumentStore) {
        contentResolver = context.getApplicationContext().getContentResolver();
        this.temporaryDocumentStore = temporaryDocumentStore;
    }

    public IncomingDocument load(Uri sourceUri, AtomicBoolean cancelled)
            throws DocumentLoadException {
        Metadata metadata = queryMetadata(sourceUri);
        if (metadata.sizeBytes >= 0L
                && metadata.sizeBytes > DocumentLimits.MAX_INCOMING_BYTES) {
            throw new DocumentLoadException(
                    DocumentLoadException.Reason.TOO_LARGE,
                    "Document exceeds the incoming file limit"
            );
        }

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

        File cachedFile = temporaryDocumentStore.copy(sourceUri, cancelled);
        long actualSize = cachedFile.length();
        try {
            byte[] prefix = readPrefix(cachedFile);
            Set<String> zipEntries = isZip(prefix)
                    ? inspectZip(cachedFile)
                    : java.util.Collections.emptySet();
            DocumentType documentType = documentTypeResolver.resolve(
                    sourceMimeType,
                    prefix,
                    zipEntries,
                    metadata.displayName
            );
            if (!DocumentLimits.isAllowedKnownSize(actualSize, documentType)) {
                throw new DocumentLoadException(
                        DocumentLoadException.Reason.TOO_LARGE,
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
        } catch (IOException | RuntimeException exception) {
            temporaryDocumentStore.delete(cachedFile);
            throw new DocumentLoadException(
                    DocumentLoadException.Reason.CORRUPTED,
                    "Unable to inspect document",
                    exception
            );
        }
    }

    private Metadata queryMetadata(Uri uri) throws DocumentLoadException {
        String fallbackName = SafeDisplayName.sanitize(uri.getLastPathSegment());
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
            String name = fallbackName;
            long size = -1L;
            int nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            if (nameColumn >= 0 && !cursor.isNull(nameColumn)) {
                name = SafeDisplayName.sanitize(cursor.getString(nameColumn));
            }
            int sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE);
            if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) {
                size = cursor.getLong(sizeColumn);
            }
            return new Metadata(name, size);
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

    private Set<String> inspectZip(File file) throws DocumentLoadException {
        Set<String> entries = new HashSet<>();
        int entryCount = 0;
        long uncompressedTotal = 0L;
        try (ZipFile zipFile = new ZipFile(file)) {
            java.util.Enumeration<? extends ZipEntry> enumeration = zipFile.entries();
            while (enumeration.hasMoreElements()) {
                ZipEntry entry = enumeration.nextElement();
                entryCount++;
                if (entryCount > DocumentLimits.MAX_ZIP_ENTRIES) {
                    throw zipLimitException();
                }
                long size = entry.getSize();
                long compressedSize = entry.getCompressedSize();
                if (size > 0L) {
                    uncompressedTotal += size;
                    if (uncompressedTotal > DocumentLimits.MAX_ZIP_UNCOMPRESSED_BYTES) {
                        throw zipLimitException();
                    }
                    if (compressedSize > 0L
                            && size / Math.max(1L, compressedSize) > DocumentLimits.MAX_ZIP_RATIO) {
                        throw zipLimitException();
                    }
                }
                String normalized = entry.getName().replace('\\', '/');
                if (!normalized.startsWith("/") && !normalized.contains("../")) {
                    entries.add(normalized);
                }
            }
            return entries;
        } catch (ZipException exception) {
            throw new DocumentLoadException(
                    DocumentLoadException.Reason.CORRUPTED,
                    "Invalid ZIP package",
                    exception
            );
        } catch (IOException exception) {
            throw new DocumentLoadException(
                    DocumentLoadException.Reason.CORRUPTED,
                    "Unable to inspect ZIP package",
                    exception
            );
        }
    }

    private DocumentLoadException zipLimitException() {
        return new DocumentLoadException(
                DocumentLoadException.Reason.TOO_LARGE,
                "Compressed document exceeds safe limits"
        );
    }

    private boolean isZip(byte[] bytes) {
        return bytes.length >= 4 && bytes[0] == 'P' && bytes[1] == 'K';
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
