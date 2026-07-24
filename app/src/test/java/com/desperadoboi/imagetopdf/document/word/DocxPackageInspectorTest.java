package com.desperadoboi.imagetopdf.document.word;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class DocxPackageInspectorTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void acceptsWordprocessingPackageAndRelationshipSelectedMainPart()
            throws Exception {
        Path conventional = DocxTestFixtures.minimalDocument(
                fixture("valid.docx"),
                DocxTestFixtures.paragraph("Hello")
        );
        DocxPackageInspector.Inspection inspection =
                DocxPackageInspector.inspect(conventional.toFile());
        assertTrue(inspection.isDocx());
        assertEquals("word/document.xml", inspection.getMainDocumentPart());

        LinkedHashMap<String, byte[]> custom = new LinkedHashMap<>();
        custom.put("[Content_Types].xml", DocxTestFixtures.bytes(
                "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                        + "<Override PartName=\"/word/main.xml\" ContentType=\""
                        + DocxPackageInspector.CONTENT_TYPE_DOCUMENT
                        + "\"/></Types>"
        ));
        custom.put("_rels/.rels", DocxTestFixtures.bytes(
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                        + "<Relationship Id=\"root\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/main.xml\"/>"
                        + "</Relationships>"
        ));
        custom.put("word/main.xml", DocxTestFixtures.bytes(
                DocxTestFixtures.wordDocument(DocxTestFixtures.paragraph("Custom"))
        ));
        Path customPath = fixture("custom.docx");
        DocxTestFixtures.writeStoredZip(customPath, custom);
        assertEquals(
                "word/main.xml",
                DocxPackageInspector.inspect(customPath.toFile()).getMainDocumentPart()
        );
    }

    @Test
    public void ordinaryZipAndMissingDocumentAreNotDocx() throws Exception {
        Map<String, byte[]> ordinary = new LinkedHashMap<>();
        ordinary.put("note.txt", DocxTestFixtures.bytes("hello"));
        Path fake = fixture("fake.docx");
        DocxTestFixtures.writeStoredZip(fake, ordinary);
        assertFalse(DocxPackageInspector.inspect(fake.toFile()).isDocx());

        LinkedHashMap<String, byte[]> missing =
                DocxTestFixtures.baseEntries("", "", "");
        missing.remove("word/document.xml");
        Path missingPath = fixture("missing.docx");
        DocxTestFixtures.writeStoredZip(missingPath, missing);
        assertFalse(DocxPackageInspector.inspect(missingPath.toFile()).isDocx());
    }

    @Test
    public void rejectsTraversalCompressionBombMacroAndExternalTemplate()
            throws Exception {
        Map<String, byte[]> traversal = new LinkedHashMap<>();
        traversal.put("../outside.xml", DocxTestFixtures.bytes("x"));
        assertReason(
                stored("traversal.docx", traversal),
                WordParseException.Reason.CORRUPTED
        );

        LinkedHashMap<String, byte[]> bomb =
                DocxTestFixtures.baseEntries(DocxTestFixtures.paragraph("x"), "", "");
        bomb.put("word/media/padding.bin", new byte[2 * 1024 * 1024]);
        Path bombPath = fixture("bomb.docx");
        DocxTestFixtures.writeDeflatedZip(bombPath, bomb);
        assertReason(bombPath, WordParseException.Reason.TOO_LARGE);

        LinkedHashMap<String, byte[]> macro =
                DocxTestFixtures.baseEntries(DocxTestFixtures.paragraph("x"), "", "");
        macro.put("word/vbaProject.bin", new byte[]{1, 2, 3});
        assertReason(
                stored("macro.docm", macro),
                WordParseException.Reason.UNSUPPORTED
        );

        LinkedHashMap<String, byte[]> template = DocxTestFixtures.baseEntries(
                DocxTestFixtures.paragraph("x"),
                DocxTestFixtures.externalRelationship(
                        "template",
                        "attachedTemplate",
                        "https://example.invalid/template.dotx"
                ),
                ""
        );
        assertReason(
                stored("template.docx", template),
                WordParseException.Reason.UNSUPPORTED
        );

        LinkedHashMap<String, byte[]> relationshipTraversal =
                DocxTestFixtures.baseEntries(DocxTestFixtures.paragraph("x"), "", "");
        relationshipTraversal.put("_rels/.rels", DocxTestFixtures.bytes(
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                        + "<Relationship Id=\"root\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" "
                        + "Target=\"word/../word/document.xml\"/></Relationships>"
        ));
        assertReason(
                stored("relationship-traversal.docx", relationshipTraversal),
                WordParseException.Reason.CORRUPTED
        );
    }

    @Test
    public void rejectsPreambleExternalImageAndMediaLimits() throws Exception {
        Path base = DocxTestFixtures.minimalDocument(
                fixture("signature-base.docx"),
                DocxTestFixtures.paragraph("x")
        );
        byte[] source = Files.readAllBytes(base);
        byte[] withPreamble = new byte[source.length + 4];
        System.arraycopy(new byte[]{'J', 'U', 'N', 'K'}, 0, withPreamble, 0, 4);
        System.arraycopy(source, 0, withPreamble, 4, source.length);
        Path preamble = fixture("preamble.docx");
        Files.write(preamble, withPreamble);
        assertReason(preamble, WordParseException.Reason.CORRUPTED);

        LinkedHashMap<String, byte[]> externalImage = DocxTestFixtures.baseEntries(
                DocxTestFixtures.paragraph("x"),
                DocxTestFixtures.externalRelationship(
                        "image",
                        "image",
                        "https://example.invalid/image.png"
                ),
                ""
        );
        assertReason(
                stored("external-image.docx", externalImage),
                WordParseException.Reason.UNSUPPORTED
        );

        LinkedHashMap<String, byte[]> tooManyImages =
                DocxTestFixtures.baseEntries(DocxTestFixtures.paragraph("x"), "", "");
        for (int index = 0; index <= com.desperadoboi.imagetopdf.document
                .DocumentLimits.MAX_WORD_IMAGES; index++) {
            tooManyImages.put("word/media/image" + index + ".png", new byte[]{1});
        }
        assertReason(
                stored("too-many-images.docx", tooManyImages),
                WordParseException.Reason.TOO_LARGE
        );

        LinkedHashMap<String, byte[]> enormousImage =
                DocxTestFixtures.baseEntries(DocxTestFixtures.paragraph("x"), "", "");
        enormousImage.put(
                "word/media/enormous.png",
                new byte[(int) com.desperadoboi.imagetopdf.document.DocumentLimits
                        .MAX_DOCX_MEDIA_ENTRY_BYTES + 1]
        );
        assertReason(
                stored("enormous-image.docx", enormousImage),
                WordParseException.Reason.TOO_LARGE
        );
    }

    @Test
    public void rejectsEncryptedZipFlag() throws Exception {
        Path base = DocxTestFixtures.minimalDocument(
                fixture("base.docx"),
                DocxTestFixtures.paragraph("x")
        );
        byte[] archive = Files.readAllBytes(base);
        for (int index = 0; index + 10 < archive.length; index++) {
            if (archive[index] == 'P' && archive[index + 1] == 'K'
                    && archive[index + 2] == 3 && archive[index + 3] == 4) {
                archive[index + 6] |= 1;
            } else if (archive[index] == 'P' && archive[index + 1] == 'K'
                    && archive[index + 2] == 1 && archive[index + 3] == 2) {
                archive[index + 8] |= 1;
            }
        }
        Path encrypted = fixture("encrypted.docx");
        Files.write(encrypted, archive);
        assertReason(encrypted, WordParseException.Reason.ENCRYPTED);
    }

    private Path fixture(String name) throws Exception {
        return temporaryFolder.newFile(name).toPath();
    }

    private Path stored(String name, Map<String, byte[]> entries) throws Exception {
        Path file = fixture(name);
        DocxTestFixtures.writeStoredZip(file, entries);
        return file;
    }

    private void assertReason(Path file, WordParseException.Reason expected)
            throws Exception {
        try {
            DocxPackageInspector.inspect(file.toFile());
            fail("Expected DOCX package rejection");
        } catch (WordParseException exception) {
            assertEquals(expected, exception.getReason());
        }
    }
}
