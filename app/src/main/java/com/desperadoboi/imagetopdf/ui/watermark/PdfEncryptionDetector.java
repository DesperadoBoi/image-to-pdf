package com.desperadoboi.imagetopdf.ui.watermark;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

final class PdfEncryptionDetector {
    private static final int MAX_TRAILER_SCAN_BYTES = 2 * 1024 * 1024;
    private static final byte[] ENCRYPT_KEY = "/Encrypt".getBytes(StandardCharsets.US_ASCII);

    private PdfEncryptionDetector() {
    }

    static boolean hasEncryptionDictionary(File file) throws IOException {
        if (file == null || !file.isFile()) return false;
        try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
            long length = input.length();
            int requested = (int) Math.min(length, MAX_TRAILER_SCAN_BYTES);
            byte[] trailer = new byte[requested];
            input.seek(length - requested);
            input.readFully(trailer);
            for (int offset = 0; offset <= trailer.length - ENCRYPT_KEY.length; offset++) {
                if (!matches(trailer, offset)) continue;
                int following = offset + ENCRYPT_KEY.length;
                if (following == trailer.length || isPdfDelimiter(trailer[following])) return true;
            }
            return false;
        }
    }

    private static boolean matches(byte[] data, int offset) {
        for (int index = 0; index < ENCRYPT_KEY.length; index++) {
            if (data[offset + index] != ENCRYPT_KEY[index]) return false;
        }
        return true;
    }

    private static boolean isPdfDelimiter(byte value) {
        int unsigned = value & 0xFF;
        return unsigned <= 0x20 || unsigned == '/' || unsigned == '<' || unsigned == '>'
                || unsigned == '[' || unsigned == ']' || unsigned == '(' || unsigned == ')';
    }
}
