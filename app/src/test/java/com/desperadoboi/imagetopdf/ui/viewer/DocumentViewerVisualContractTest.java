package com.desperadoboi.imagetopdf.ui.viewer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

public final class DocumentViewerVisualContractTest {
    private static final String ANDROID = "http://schemas.android.com/apk/res/android";

    @Test
    public void documentModesUseContentSpecificSurfaces() throws Exception {
        Document document = parse(repositoryRoot().resolve(
                "app/src/main/res/layout/activity_document_viewer.xml"
        ));

        assertEquals(
                "@color/viewer_preview_background",
                byId(document, "@+id/content_viewer_pdf").getAttributeNS(ANDROID, "background")
        );
        assertEquals(
                "@color/viewer_image_background",
                byId(document, "@+id/content_viewer_image").getAttributeNS(ANDROID, "background")
        );
        assertEquals(
                "@drawable/bg_viewer_text_surface",
                byId(document, "@+id/content_viewer_text").getAttributeNS(ANDROID, "background")
        );
        assertEquals(
                "@color/viewer_document_surface",
                byId(document, "@+id/content_viewer_spreadsheet")
                        .getAttributeNS(ANDROID, "background")
        );
    }

    @Test
    public void spreadsheetControlsStayInOneCompactRow() throws Exception {
        Document document = parse(repositoryRoot().resolve(
                "app/src/main/res/layout/activity_document_viewer.xml"
        ));
        Element controls = byId(document, "@+id/layout_viewer_sheet_controls");
        Element sheetName = byId(document, "@+id/text_viewer_sheet_name");
        Element spinner = byId(document, "@+id/spinner_viewer_sheets");

        assertEquals("wrap_content", controls.getAttributeNS(ANDROID, "layout_height"));
        assertEquals("@dimen/viewer_toolbar_height", controls.getAttributeNS(ANDROID, "minHeight"));
        assertEquals("1", sheetName.getAttributeNS(ANDROID, "maxLines"));
        assertEquals("end", sheetName.getAttributeNS(ANDROID, "ellipsize"));
        assertEquals("@dimen/touch_target", spinner.getAttributeNS(ANDROID, "layout_height"));
    }

    @Test
    public void spreadsheetUsesUnifiedTwoDimensionalViewport() throws Exception {
        Document document = parse(repositoryRoot().resolve(
                "app/src/main/res/layout/activity_document_viewer.xml"
        ));
        Element viewport = byId(document, "@+id/viewport_viewer_spreadsheet");
        Element fitWidth = byId(document, "@+id/button_viewer_fit_width");

        assertEquals("com.desperadoboi.imagetopdf.ui.viewer.SpreadsheetViewport",
                viewport.getTagName());
        assertEquals("@string/viewer_fit_width_content_description",
                fitWidth.getAttributeNS(ANDROID, "contentDescription"));
        assertEquals(0, document.getElementsByTagName("HorizontalScrollView").getLength());
    }

    @Test
    public void neutralDocumentPaletteDoesNotSwitchToDarkMode() throws Exception {
        Document baseColors = parse(repositoryRoot().resolve(
                "app/src/main/res/values/colors.xml"
        ));
        Document nightColors = parse(repositoryRoot().resolve(
                "app/src/main/res/values-night/colors.xml"
        ));
        String[] neutralColors = {
                "viewer_preview_background",
                "viewer_document_surface",
                "viewer_document_text",
                "viewer_table_grid",
                "viewer_table_column_header",
                "viewer_table_row_header"
        };

        for (String color : neutralColors) {
            assertNotNull(findNamed(baseColors, "color", color));
            assertFalse("Night mode must keep neutral document color " + color,
                    findNamed(nightColors, "color", color) != null);
        }
    }

    private static Element byId(Document document, String id) {
        NodeList elements = document.getElementsByTagName("*");
        for (int index = 0; index < elements.getLength(); index++) {
            Element element = (Element) elements.item(index);
            if (id.equals(element.getAttributeNS(ANDROID, "id"))) return element;
        }
        throw new AssertionError("Missing view " + id);
    }

    private static Element findNamed(Document document, String tag, String name) {
        NodeList elements = document.getElementsByTagName(tag);
        for (int index = 0; index < elements.getLength(); index++) {
            Element element = (Element) elements.item(index);
            if (name.equals(element.getAttribute("name"))) return element;
        }
        return null;
    }

    private static Document parse(Path path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "");
        factory.setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "");
        return factory.newDocumentBuilder().parse(path.toFile());
    }

    private static Path repositoryRoot() {
        Path current = Paths.get("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("app/src/main"))) return current;
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("app/src/main"))) return parent;
        throw new IllegalStateException("Repository root was not found from " + current);
    }
}
