package com.desperadoboi.imagetopdf.ui.watermark;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class PdfEncryptionDetectorTest {
    @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test public void trailerEncryptionEntryIsDetected() throws Exception {
        File file = temporaryFolder.newFile("encrypted.pdf");
        Files.write(file.toPath(),
                "%PDF-1.7\ntrailer << /Size 5 /Encrypt 4 0 R >>\n%%EOF"
                        .getBytes(StandardCharsets.US_ASCII));
        assertTrue(PdfEncryptionDetector.hasEncryptionDictionary(file));
    }

    @Test public void ordinaryTrailerIsNotEncrypted() throws Exception {
        File file = temporaryFolder.newFile("plain.pdf");
        Files.write(file.toPath(),
                "%PDF-1.7\ntrailer << /Size 5 /Root 1 0 R >>\n%%EOF"
                        .getBytes(StandardCharsets.US_ASCII));
        assertFalse(PdfEncryptionDetector.hasEncryptionDictionary(file));
    }

    @Test public void longerNameDoesNotProduceFalseMatch() throws Exception {
        File file = temporaryFolder.newFile("plain.pdf");
        Files.write(file.toPath(),
                "%PDF-1.7\n<< /EncryptedLabel (none) >>\n%%EOF"
                        .getBytes(StandardCharsets.US_ASCII));
        assertFalse(PdfEncryptionDetector.hasEncryptionDictionary(file));
    }
}
