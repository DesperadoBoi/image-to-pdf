package com.desperadoboi.imagetopdf.ui.tools;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class AllToolsLayoutDensityTest {
    private static final String ANDROID = "http://schemas.android.com/apk/res/android";

    @Test
    public void catalogItemsUseContentHeightAndDoNotReserveDescriptionSpace() throws Exception {
        Document document = parse(repositoryRoot().resolve(
                "app/src/main/res/layout/item_catalog_tool.xml"
        ));
        Element root = document.getDocumentElement();
        assertEquals("wrap_content", root.getAttributeNS(ANDROID, "layout_height"));
        assertEquals("@dimen/tool_grid_half_gap", root.getAttributeNS(ANDROID, "layout_margin"));
        assertEquals("@dimen/tool_item_vertical_padding",
                root.getAttributeNS(ANDROID, "paddingTop"));
        assertEquals("@dimen/tool_item_vertical_padding",
                root.getAttributeNS(ANDROID, "paddingBottom"));

        Element title = byId(document, "@+id/text_tool_title");
        assertEquals("2", title.getAttributeNS(ANDROID, "maxLines"));
        assertFalse(title.hasAttributeNS(ANDROID, "minLines"));

        Element description = byId(document, "@+id/text_tool_description");
        assertEquals("1", description.getAttributeNS(ANDROID, "maxLines"));
        assertEquals("end", description.getAttributeNS(ANDROID, "ellipsize"));
        assertEquals("gone", description.getAttributeNS(ANDROID, "visibility"));
    }

    @Test
    public void threeColumnGeometryFitsSupportedPhoneWidths() throws Exception {
        Document dimensions = parse(repositoryRoot().resolve(
                "app/src/main/res/values/dimens.xml"
        ));
        assertFalse(hasDimension(dimensions, "catalog_tool_item_height"));
        assertEquals(4, dimensionDp(dimensions, "tool_grid_half_gap"));
        assertEquals(60, dimensionDp(dimensions, "tool_icon_size"));
        assertEquals(68, dimensionDp(dimensions, "tool_icon_area_height"));
        assertEquals(6, dimensionDp(dimensions, "tool_item_vertical_padding"));
        assertEquals(6, dimensionDp(dimensions, "tool_title_spacing"));
        assertEquals(4, dimensionDp(dimensions, "tool_description_spacing"));

        for (int widthDp : new int[]{320, 360, 393, 412}) {
            double contentWidth = widthDp / 3.0 - 2 * dimensionDp(
                    dimensions,
                    "tool_grid_half_gap"
            );
            assertTrue("Icon must fit at " + widthDp + "dp", contentWidth >= 60.0);
        }
    }

    private static Element byId(Document document, String id) {
        NodeList elements = document.getElementsByTagName("TextView");
        for (int index = 0; index < elements.getLength(); index++) {
            Element element = (Element) elements.item(index);
            if (id.equals(element.getAttributeNS(ANDROID, "id"))) return element;
        }
        throw new AssertionError("Missing view " + id);
    }

    private static boolean hasDimension(Document document, String name) {
        NodeList dimensions = document.getElementsByTagName("dimen");
        for (int index = 0; index < dimensions.getLength(); index++) {
            Element dimension = (Element) dimensions.item(index);
            if (name.equals(dimension.getAttribute("name"))) return true;
        }
        return false;
    }

    private static int dimensionDp(Document document, String name) {
        NodeList dimensions = document.getElementsByTagName("dimen");
        for (int index = 0; index < dimensions.getLength(); index++) {
            Element dimension = (Element) dimensions.item(index);
            if (name.equals(dimension.getAttribute("name"))) {
                return Integer.parseInt(dimension.getTextContent().replace("dp", ""));
            }
        }
        throw new AssertionError("Missing dimension " + name);
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
