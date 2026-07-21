package com.desperadoboi.imagetopdf.image;

import android.content.Context;
import android.net.Uri;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;

public final class CapturedImageStorage {
    public static final String DIRECTORY_NAME = "captured_images";

    private static final int MAX_CREATE_ATTEMPTS = 5;

    private final Context applicationContext;
    private final File capturedImagesDirectory;
    private final CapturedImageFileNameGenerator fileNameGenerator;

    public CapturedImageStorage(Context context) {
        applicationContext = context.getApplicationContext();
        capturedImagesDirectory = new File(applicationContext.getFilesDir(), DIRECTORY_NAME);
        fileNameGenerator = new CapturedImageFileNameGenerator();
    }

    public CapturedImage createCapturedImage() throws IOException {
        ensureDirectoryExists();
        IOException lastException = null;
        for (int attempt = 0; attempt < MAX_CREATE_ATTEMPTS; attempt++) {
            String fileName = fileNameGenerator.createFileName();
            File file = resolveFile(fileName);
            try {
                if (file.createNewFile()) {
                    return new CapturedImage(
                            fileName,
                            FileProvider.getUriForFile(applicationContext, getAuthority(), file),
                            file
                    );
                }
            } catch (IOException exception) {
                lastException = exception;
            }
        }
        if (lastException != null) {
            throw lastException;
        }
        throw new IOException("Unable to create unique captured image file");
    }

    public boolean existsAndHasContent(String fileName) {
        File file = resolveFileIfValid(fileName);
        return file != null && file.isFile() && file.length() > 0L;
    }

    public void delete(String fileName) {
        File file = resolveFileIfValid(fileName);
        if (file == null || !file.exists()) {
            return;
        }
        try {
            file.delete();
        } catch (SecurityException exception) {
            // Best effort cleanup only.
        }
    }

    private void ensureDirectoryExists() throws IOException {
        if (capturedImagesDirectory.isDirectory()) {
            return;
        }
        if (!capturedImagesDirectory.mkdirs() && !capturedImagesDirectory.isDirectory()) {
            throw new IOException("Unable to create captured images directory");
        }
    }

    private String getAuthority() {
        return applicationContext.getPackageName() + ".fileprovider";
    }

    private File resolveFile(String fileName) {
        return new File(capturedImagesDirectory, fileName);
    }

    private File resolveFileIfValid(String fileName) {
        if (!CapturedImageFileNameGenerator.isGeneratedFileName(fileName)) {
            return null;
        }
        return resolveFile(fileName.trim());
    }

    public static final class CapturedImage {
        private final String fileName;
        private final Uri uri;
        private final File file;

        private CapturedImage(String fileName, Uri uri, File file) {
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
