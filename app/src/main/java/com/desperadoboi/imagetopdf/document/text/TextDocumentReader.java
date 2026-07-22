package com.desperadoboi.imagetopdf.document.text;

import com.desperadoboi.imagetopdf.document.DocumentLimits;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class TextDocumentReader {
    private static final int ENCODING_SAMPLE_BYTES = 16 * 1024;

    public TextPreview read(File file) throws IOException {
        Charset charset = detectCharset(file);
        List<String> lines = new ArrayList<>();
        boolean truncated = false;
        int characterCount = 0;
        try (BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(file))) {
            skipUtf8Bom(inputStream);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, charset))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    int remaining = DocumentLimits.MAX_TEXT_PREVIEW_CHARS - characterCount;
                    if (remaining <= 0 || lines.size() >= DocumentLimits.MAX_TEXT_LINES) {
                        truncated = true;
                        break;
                    }
                    int offset = 0;
                    if (line.isEmpty()) {
                        lines.add("");
                    }
                    while (offset < line.length()) {
                        if (lines.size() >= DocumentLimits.MAX_TEXT_LINES
                                || characterCount >= DocumentLimits.MAX_TEXT_PREVIEW_CHARS) {
                            truncated = true;
                            break;
                        }
                        int length = Math.min(
                                DocumentLimits.MAX_TEXT_LINE_CHARS,
                                Math.min(
                                        line.length() - offset,
                                        DocumentLimits.MAX_TEXT_PREVIEW_CHARS - characterCount
                                )
                        );
                        lines.add(line.substring(offset, offset + length));
                        offset += length;
                        characterCount += length;
                    }
                    characterCount++;
                    if (truncated) break;
                }
            }
        }
        return new TextPreview(lines, truncated);
    }

    public Charset detectCharset(File file) throws IOException {
        byte[] sample = new byte[(int) Math.min(file.length(), ENCODING_SAMPLE_BYTES)];
        try (FileInputStream inputStream = new FileInputStream(file)) {
            int read = inputStream.read(sample);
            if (read < 0) read = 0;
            if (read != sample.length) {
                byte[] shortened = new byte[read];
                System.arraycopy(sample, 0, shortened, 0, read);
                sample = shortened;
            }
        }
        int offset = hasUtf8Bom(sample) ? 3 : 0;
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(sample, offset, sample.length - offset));
            return StandardCharsets.UTF_8;
        } catch (CharacterCodingException exception) {
            return Charset.forName("windows-1251");
        }
    }

    private void skipUtf8Bom(BufferedInputStream inputStream) throws IOException {
        inputStream.mark(3);
        byte[] bom = new byte[3];
        int read = inputStream.read(bom);
        if (read != 3 || !hasUtf8Bom(bom)) {
            inputStream.reset();
        }
    }

    private boolean hasUtf8Bom(byte[] bytes) {
        return bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF;
    }
}
