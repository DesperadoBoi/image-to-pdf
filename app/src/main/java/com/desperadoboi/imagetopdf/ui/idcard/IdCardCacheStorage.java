package com.desperadoboi.imagetopdf.ui.idcard;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

public final class IdCardCacheStorage {
    public static final String DIRECTORY_NAME = "id_card";
    public static final long DEFAULT_TTL_MS = 24L * 60L * 60L * 1000L;

    private static final long MAX_SOURCE_BYTES = 64L * 1024L * 1024L;
    private static final String PREFIX = "idcard_";
    private static final String SAFE_NAME = "idcard_[A-Za-z0-9_-]+\\.(jpg|img)";

    private final Context applicationContext;
    private final File directory;

    public IdCardCacheStorage(Context context) {
        applicationContext = context.getApplicationContext();
        directory = new File(applicationContext.getCacheDir(), DIRECTORY_NAME);
    }

    public CacheImage createCameraImage() throws IOException {
        return createEmpty(".jpg");
    }

    public CacheImage copyFrom(Uri sourceUri) throws IOException {
        if (sourceUri == null) {
            throw new IllegalArgumentException("sourceUri is required");
        }
        CacheImage image = createEmpty(".img");
        boolean completed = false;
        try (InputStream input = openInputStream(sourceUri);
             FileOutputStream output = new FileOutputStream(image.getFile(), false)) {
            byte[] buffer = new byte[16 * 1024];
            long total = 0L;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_SOURCE_BYTES) {
                    throw new IOException("Image exceeds the local import limit");
                }
                output.write(buffer, 0, read);
            }
            output.flush();
            if (total <= 0L) {
                throw new IOException("Image is empty");
            }
            completed = true;
            return image;
        } finally {
            if (!completed) {
                delete(image.getFileName());
            }
        }
    }

    public boolean existsAndHasContent(String fileName) {
        File file = resolveValid(fileName);
        return file != null && file.isFile() && file.length() > 0L;
    }

    public void delete(String fileName) {
        File file = resolveValid(fileName);
        if (file == null || !file.exists()) {
            return;
        }
        try {
            file.delete();
        } catch (SecurityException ignored) {
            // Best-effort removal of app-cache data.
        }
    }

    public int cleanupExpired(long nowMillis, long ttlMillis) {
        if (ttlMillis < 0L || !directory.isDirectory()) {
            return 0;
        }
        File[] files = directory.listFiles();
        if (files == null) {
            return 0;
        }
        int removed = 0;
        long cutoff = nowMillis - ttlMillis;
        for (File file : files) {
            if (file.isFile()
                    && isGeneratedFileName(file.getName())
                    && file.lastModified() < cutoff
                    && file.delete()) {
                removed++;
            }
        }
        return removed;
    }

    public static boolean isGeneratedFileName(String fileName) {
        return fileName != null && fileName.trim().matches(SAFE_NAME);
    }

    private CacheImage createEmpty(String extension) throws IOException {
        ensureDirectory();
        for (int attempt = 0; attempt < 5; attempt++) {
            String fileName = PREFIX + UUID.randomUUID() + extension;
            File file = new File(directory, fileName);
            if (file.createNewFile()) {
                Uri uri = FileProvider.getUriForFile(
                        applicationContext,
                        applicationContext.getPackageName() + ".fileprovider",
                        file
                );
                return new CacheImage(fileName, uri, file);
            }
        }
        throw new IOException("Unable to create ID-card cache file");
    }

    private void ensureDirectory() throws IOException {
        if (directory.isDirectory()) {
            return;
        }
        if (!directory.mkdirs() && !directory.isDirectory()) {
            throw new IOException("Unable to create ID-card cache directory");
        }
    }

    private InputStream openInputStream(Uri uri) throws IOException {
        ContentResolver resolver = applicationContext.getContentResolver();
        InputStream stream = resolver.openInputStream(uri);
        if (stream == null) {
            throw new IOException("Unable to open selected image");
        }
        return stream;
    }

    private File resolveValid(String fileName) {
        if (!isGeneratedFileName(fileName)) {
            return null;
        }
        return new File(directory, fileName.trim());
    }

    public static final class CacheImage {
        private final String fileName;
        private final Uri uri;
        private final File file;

        private CacheImage(String fileName, Uri uri, File file) {
            this.fileName = fileName;
            this.uri = uri;
            this.file = file;
        }

        public String getFileName() {
            return fileName;
        }

        public Uri getUri() {
            return uri;
        }

        public File getFile() {
            return file;
        }
    }
}
