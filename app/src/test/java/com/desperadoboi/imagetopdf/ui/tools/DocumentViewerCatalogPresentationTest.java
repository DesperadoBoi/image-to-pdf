package com.desperadoboi.imagetopdf.ui.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

public final class DocumentViewerCatalogPresentationTest {
    private static final String DESCRIPTION_KEY = "tool_document_viewer_description";

    @Test
    public void localizedPresentationIsCompactAndListsPrimaryFormats() throws Exception {
        String ruTitle = readString(
                repositoryRoot().resolve("app/src/main/res/values/strings.xml"),
                "tool_document_viewer"
        );
        String enTitle = readString(
                repositoryRoot().resolve("app/src/main/res/values-en/viewer_strings.xml"),
                "tool_document_viewer"
        );
        String ru = readString(
                repositoryRoot().resolve("app/src/main/res/values/strings.xml"),
                DESCRIPTION_KEY
        );
        String en = readString(
                repositoryRoot().resolve("app/src/main/res/values-en/viewer_strings.xml"),
                DESCRIPTION_KEY
        );

        assertEquals("Просмотр файлов", ruTitle);
        assertEquals("File viewer", enTitle);
        assertEquals("PDF, XLSX, CSV и изображения", ru);
        assertEquals("PDF, XLSX, CSV, and images", en);
        assertFalse(ru.toLowerCase(Locale.ROOT).contains("excel"));
        assertFalse(en.toLowerCase(Locale.ROOT).contains("excel"));
        assertContainsAll(
                ru.toLowerCase(Locale.ROOT),
                "pdf", "xlsx", "csv", "изображен"
        );
        assertContainsAll(
                en.toLowerCase(Locale.ROOT),
                "pdf", "xlsx", "csv", "image"
        );
        assertFalse(ru.toLowerCase(Locale.ROOT).contains("таблиц"));
        assertFalse(en.toLowerCase(Locale.ROOT).contains("table"));
    }

    @Test
    public void viewerRemainsAvailableWithoutComingSoonBadge() {
        assertEquals(
                ToolAvailability.AVAILABLE,
                ToolCatalog.get(ToolId.DOCUMENT_VIEWER).getAvailability()
        );
    }

    @Test
    public void oldXlsMessageIsExplicitAndActionable() throws Exception {
        assertEquals(
                "Старый формат XLS пока не поддерживается. Сохраните файл как XLSX.",
                readString(
                        repositoryRoot().resolve("app/src/main/res/values/strings.xml"),
                        "viewer_error_xls_not_supported"
                )
        );
    }

    private static void assertContainsAll(String value, String... expectedParts) {
        for (String expectedPart : expectedParts) {
            assertTrue(value + " is missing " + expectedPart, value.contains(expectedPart));
        }
    }

    private static String readString(Path path, String name) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "");
        factory.setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "");
        Document document = factory.newDocumentBuilder().parse(path.toFile());
        NodeList strings = document.getElementsByTagName("string");
        for (int index = 0; index < strings.getLength(); index++) {
            Element string = (Element) strings.item(index);
            if (name.equals(string.getAttribute("name"))) {
                return string.getTextContent();
            }
        }
        throw new AssertionError(path + " is missing " + name);
    }

    private static Path repositoryRoot() {
        Path current = Paths.get("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("app/src/main"))) return current;
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("app/src/main"))) return parent;
        throw new IllegalStateException("Repository root was not found from " + current);
    }
}
