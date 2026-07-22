package com.desperadoboi.imagetopdf.document;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TemporaryDocumentStore {
    public static final String CACHE_DIRECTORY = "document_viewer";
    private static final long MAX_AGE_MILLIS = 24L * 60L * 60L * 1000L;
    private static final int COPY_BUFFER_BYTES = 32 * 1024;

    private final ContentResolver contentResolver;
    private final File cacheDirectory;

    public TemporaryDocumentStore(Context context) {
        Context applicationContext = Objects.requireNonNull(context, "context is required")
                .getApplicationContext();
        contentResolver = applicationContext.getContentResolver();
        cacheDirectory = new File(applicationContext.getCacheDir(), CACHE_DIRECTORY);
    }

    public File copy(Uri sourceUri, AtomicBoolean cancelled) throws DocumentLoadException {
        ensureCacheDirectory();
        File destination = new File(cacheDirectory, "viewer_" + UUID.randomUUID() + ".cache");
        long copied = 0L;
        try (InputStream inputStream = contentResolver.openInputStream(sourceUri);
             FileOutputStream outputStream = new FileOutputStream(destination)) {
            if (inputStream == null) {
                throw new DocumentLoadException(
                        DocumentLoadException.Reason.UNREADABLE,
                        "Provider returned no input stream"
                );
            }
            byte[] buffer = new byte[COPY_BUFFER_BYTES];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                if (cancelled != null && cancelled.get()) {
                    throw new DocumentLoadException(
                            DocumentLoadException.Reason.CANCELLED,
                            "Document loading was cancelled"
                    );
                }
                copied += read;
                if (copied > DocumentLimits.MAX_INCOMING_BYTES) {
                    throw new DocumentLoadException(
                            DocumentLoadException.Reason.TOO_LARGE,
                            "Document exceeds the cache limit"
                    );
                }
                outputStream.write(buffer, 0, read);
            }
            outputStream.getFD().sync();
            return destination;
        } catch (SecurityException exception) {
            delete(destination);
            throw new DocumentLoadException(
                    DocumentLoadException.Reason.PERMISSION_LOST,
                    "Read permission is unavailable",
                    exception
            );
        } catch (DocumentLoadException exception) {
            delete(destination);
            throw exception;
        } catch (IOException | RuntimeException exception) {
            delete(destination);
            throw new DocumentLoadException(
                    DocumentLoadException.Reason.UNREADABLE,
                    "Unable to cache document",
                    exception
            );
        }
    }

    public void cleanupOldFiles() {
        File[] files = cacheDirectory.listFiles();
        if (files == null) {
            return;
        }
        long cutoff = System.currentTimeMillis() - MAX_AGE_MILLIS;
        for (File file : files) {
            if (file.isFile() && file.getName().startsWith("viewer_")
                    && file.lastModified() < cutoff) {
                delete(file);
            }
        }
    }

    public void delete(File file) {
        if (file != null && isOwnedCacheFile(file) && file.isFile()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    public boolean isOwnedCacheFile(File file) {
        if (file == null || !file.getName().startsWith("viewer_")) {
            return false;
        }
        try {
            return file.getCanonicalFile().getParentFile().equals(cacheDirectory.getCanonicalFile());
        } catch (IOException exception) {
            return false;
        }
    }

    private void ensureCacheDirectory() throws DocumentLoadException {
        if ((cacheDirectory.isDirectory() || cacheDirectory.mkdirs())
                && cacheDirectory.isDirectory()) {
            return;
        }
        throw new DocumentLoadException(
                DocumentLoadException.Reason.UNREADABLE,
                "Unable to create document cache"
        );
    }
}
