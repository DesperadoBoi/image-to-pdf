package com.desperadoboi.imagetopdf.ui.viewer;

import android.graphics.BitmapFactory;
import android.util.Base64;

import androidx.annotation.Nullable;

import com.desperadoboi.imagetopdf.document.word.WordImage;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class DocxLocalImageStore implements DocxImageSource, Closeable {
    private static final int MAX_IMAGE_BYTES = 16 * 1024 * 1024;
    private static final int MAX_TOTAL_SOURCE_BYTES = 16 * 1024 * 1024;

    private final ZipFile packageFile;
    private final Map<String, String> dataUris = new HashMap<>();
    private int encodedSourceBytes;
    private boolean closed;

    DocxLocalImageStore(File file) throws IOException {
        packageFile = new ZipFile(file);
    }

    @Nullable
    @Override
    public String dataUri(WordImage image) {
        if (closed || image == null || image.isVectorPlaceholder()) return null;
        String path = image.getPackagePath();
        if (!isSafePackagePath(path)) return null;
        String cached = dataUris.get(path);
        if (cached != null) return cached;
        try {
            ZipEntry entry = packageFile.getEntry(path);
            if (entry == null || entry.isDirectory()
                    || entry.getSize() > MAX_IMAGE_BYTES
                    || entry.getSize() < 0L) {
                return null;
            }
            int sourceSize = (int) entry.getSize();
            if (encodedSourceBytes > MAX_TOTAL_SOURCE_BYTES - sourceSize) return null;
            byte[] bytes;
            try (InputStream inputStream = packageFile.getInputStream(entry)) {
                bytes = readBounded(inputStream, sourceSize);
            }
            String mime = verifiedMime(bytes);
            if (mime == null || !isSafelyDecodable(bytes)) return null;
            String dataUri = "data:" + mime + ";base64,"
                    + Base64.encodeToString(bytes, Base64.NO_WRAP);
            encodedSourceBytes += bytes.length;
            dataUris.put(path, dataUri);
            return dataUri;
        } catch (IOException | RuntimeException | OutOfMemoryError ignored) {
            return null;
        }
    }

    private byte[] readBounded(InputStream inputStream, int expectedSize) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(
                Math.max(32, Math.min(expectedSize, MAX_IMAGE_BYTES))
        );
        byte[] buffer = new byte[16 * 1024];
        int total = 0;
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            total += read;
            if (total > MAX_IMAGE_BYTES) throw new IOException("DOCX image exceeds limit");
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    @Nullable
    private String verifiedMime(byte[] bytes) {
        if (bytes.length >= 8
                && unsigned(bytes[0]) == 0x89
                && bytes[1] == 'P'
                && bytes[2] == 'N'
                && bytes[3] == 'G'
                && unsigned(bytes[4]) == 0x0D
                && unsigned(bytes[5]) == 0x0A
                && unsigned(bytes[6]) == 0x1A
                && unsigned(bytes[7]) == 0x0A) {
            return "image/png";
        }
        if (bytes.length >= 3
                && unsigned(bytes[0]) == 0xFF
                && unsigned(bytes[1]) == 0xD8
                && unsigned(bytes[2]) == 0xFF) {
            return "image/jpeg";
        }
        if (bytes.length >= 12
                && ascii(bytes, 0, "RIFF")
                && ascii(bytes, 8, "WEBP")) {
            return "image/webp";
        }
        if (bytes.length >= 6
                && (ascii(bytes, 0, "GIF87a") || ascii(bytes, 0, "GIF89a"))) {
            return "image/gif";
        }
        if (bytes.length >= 2 && bytes[0] == 'B' && bytes[1] == 'M') {
            return "image/bmp";
        }
        return null;
    }

    private boolean isSafelyDecodable(byte[] bytes) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        return options.outWidth > 0 && options.outHeight > 0;
    }

    private boolean isSafePackagePath(String value) {
        if (value == null || value.isEmpty() || value.startsWith("/")
                || value.startsWith("\\") || value.contains("\\")
                || value.indexOf('\0') >= 0) {
            return false;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        return !normalized.contains("../")
                && !normalized.endsWith("/..")
                && !normalized.contains("://");
    }

    private boolean ascii(byte[] bytes, int offset, String expected) {
        if (bytes.length < offset + expected.length()) return false;
        for (int index = 0; index < expected.length(); index++) {
            if (bytes[offset + index] != (byte) expected.charAt(index)) return false;
        }
        return true;
    }

    private int unsigned(byte value) {
        return value & 0xFF;
    }

    @Override
    public void close() throws IOException {
        if (closed) return;
        closed = true;
        dataUris.clear();
        packageFile.close();
    }
}
