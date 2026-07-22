package com.desperadoboi.imagetopdf.document.text;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class TextDocumentReaderTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void readsUtf8BomWithoutDisplayingIt() throws Exception {
        File fixture = temporaryFolder.newFile("utf8-bom.txt");
        try (FileOutputStream output = new FileOutputStream(fixture)) {
            output.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
            output.write("Привет\nмир".getBytes(StandardCharsets.UTF_8));
        }

        TextPreview preview = new TextDocumentReader().read(fixture);
        assertEquals("Привет", preview.getLines().get(0));
        assertEquals("мир", preview.getLines().get(1));
        assertFalse(preview.isTruncated());
    }

    @Test
    public void fallsBackForWindows1251Fixture() throws Exception {
        File fixture = temporaryFolder.newFile("legacy.txt");
        try (FileOutputStream output = new FileOutputStream(fixture)) {
            output.write("Текст".getBytes(Charset.forName("windows-1251")));
        }

        assertEquals("Текст", new TextDocumentReader().read(fixture).getLines().get(0));
    }
}
