package com.desperadoboi.imagetopdf.ui.tools;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
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
    private static final String APP = "http://schemas.android.com/apk/res-auto";

    @Test
    public void catalogItemsUseContentHeightAndOnlyShowIconTitleAndBadge() throws Exception {
        Document document = parse(repositoryRoot().resolve(
                "app/src/main/res/layout/item_catalog_tool.xml"
        ));
        Element root = document.getDocumentElement();
        assertEquals("wrap_content", root.getAttributeNS(ANDROID, "layout_height"));
        assertEquals("@dimen/catalog_tool_grid_half_gap",
                root.getAttributeNS(ANDROID, "layout_margin"));
        assertEquals("@dimen/catalog_tool_item_vertical_padding",
                root.getAttributeNS(ANDROID, "paddingTop"));
        assertEquals("@dimen/catalog_tool_item_vertical_padding",
                root.getAttributeNS(ANDROID, "paddingBottom"));
        assertEquals("true", root.getAttributeNS(ANDROID, "clickable"));
        assertEquals("true", root.getAttributeNS(ANDROID, "focusable"));

        Element title = byId(document, "@+id/text_tool_title");
        assertEquals("2", title.getAttributeNS(ANDROID, "maxLines"));
        assertEquals("2", title.getAttributeNS(ANDROID, "minLines"));
        assertEquals("balanced", title.getAttributeNS(ANDROID, "breakStrategy"));
        assertEquals("end", title.getAttributeNS(ANDROID, "ellipsize"));
        assertEquals("@color/catalog_tool_title",
                title.getAttributeNS(ANDROID, "textColor"));
        assertFalse(hasElementWithId(document, "@+id/text_tool_description"));
    }

    @Test
    public void threeColumnGeometryFitsSupportedPhoneWidths() throws Exception {
        assertFalse(hasDimension("catalog_tool_item_height"));
        assertEquals(4, dimensionDp("catalog_tool_grid_half_gap"));
        assertEquals(60, dimensionDp("tool_icon_size"));
        assertEquals(68, dimensionDp("tool_icon_area_height"));
        assertEquals(6, dimensionDp("catalog_tool_item_vertical_padding"));
        assertEquals(6, dimensionDp("catalog_tool_title_spacing"));

        for (int widthDp : new int[]{320, 360, 393, 412}) {
            double screenContentWidth = widthDp - 2.0 * dimensionDp(
                    "screen_content_padding"
            );
            double contentWidth = screenContentWidth / 3.0
                    - 2 * dimensionDp("catalog_tool_grid_half_gap");
            assertTrue("Icon must fit at " + widthDp + "dp", contentWidth >= 60.0);
        }
    }

    @Test
    public void catalogRhythmMatchesToolbarRowsAndSections() throws Exception {
        assertEquals(28, dimensionDp("catalog_section_top_spacing"));
        assertEquals(8, dimensionDp("catalog_section_bottom_spacing"));

        int titleToFirstIcon = dimensionDp("catalog_section_bottom_spacing")
                + dimensionDp("catalog_tool_grid_half_gap")
                + dimensionDp("catalog_tool_item_vertical_padding");
        int betweenRows = 2 * dimensionDp("catalog_tool_item_vertical_padding")
                + 2 * dimensionDp("catalog_tool_grid_half_gap");
        int lastRowToNextTitle = dimensionDp("catalog_tool_item_vertical_padding")
                + dimensionDp("catalog_tool_grid_half_gap")
                + dimensionDp("catalog_section_top_spacing");

        assertEquals(18, titleToFirstIcon);
        assertEquals(20, betweenRows);
        assertEquals(38, lastRowToNextTitle);

        Document fragment = parse(repositoryRoot().resolve(
                "app/src/main/res/layout/fragment_all_tools.xml"
        ));
        Element toolbar = byId(fragment, "@+id/layout_all_tools_toolbar");
        assertEquals("@dimen/touch_target",
                toolbar.getAttributeNS(ANDROID, "layout_height"));
        Element recycler = byId(fragment, "@+id/recycler_all_tools");
        assertFalse(recycler.hasAttributeNS(ANDROID, "layout_marginTop"));
        assertEquals("@id/layout_all_tools_toolbar",
                recycler.getAttributeNS(APP, "layout_constraintTop_toBottomOf"));

        Element divider = byId(fragment, "@+id/divider_all_tools_toolbar");
        assertEquals("@dimen/catalog_toolbar_divider_height",
                divider.getAttributeNS(ANDROID, "layout_height"));
        assertEquals("?attr/colorOutline",
                divider.getAttributeNS(ANDROID, "background"));
        assertEquals(1, dimensionDp("catalog_toolbar_divider_height"));
    }

    @Test
    public void badgeIsCompactAnchoredAndNotAnAccessibilityNode() throws Exception {
        Document item = parse(repositoryRoot().resolve(
                "app/src/main/res/layout/item_catalog_tool.xml"
        ));
        Element badge = byId(item, "@+id/text_tool_badge");
        assertEquals("top|end", badge.getAttributeNS(ANDROID, "layout_gravity"));
        assertEquals("1", badge.getAttributeNS(ANDROID, "maxLines"));
        assertEquals("no", badge.getAttributeNS(ANDROID, "importantForAccessibility"));
        assertEquals("@drawable/bg_catalog_tool_badge",
                badge.getAttributeNS(ANDROID, "background"));
        assertEquals("?attr/colorOnSurfaceVariant",
                badge.getAttributeNS(ANDROID, "textColor"));
        assertEquals(4, dimensionDp("catalog_tool_badge_horizontal_padding"));
        assertEquals(1, dimensionDp("catalog_tool_badge_vertical_padding"));
        assertEquals(10, dimensionSp("catalog_tool_badge_text_size"));

        Document background = parse(repositoryRoot().resolve(
                "app/src/main/res/drawable/bg_catalog_tool_badge.xml"
        ));
        Element solid = (Element) background.getElementsByTagName("solid").item(0);
        assertEquals("?attr/colorSurfaceVariant",
                solid.getAttributeNS(ANDROID, "color"));
    }

    @Test
    public void activeAndDisabledStatesKeepOneAccessibleClickableNode() throws Exception {
        String adapter = Files.readString(
                repositoryRoot().resolve(
                        "app/src/main/java/com/desperadoboi/imagetopdf/ui/tools/"
                                + "AllToolsAdapter.java"
                ),
                StandardCharsets.UTF_8
        ).replaceAll("\\s+", " ");
        assertTrue(adapter.contains(
                "holder.badge.setVisibility(available ? View.GONE : View.VISIBLE);"
        ));
        assertTrue(adapter.contains("holder.title.setEnabled(available);"));
        assertTrue(adapter.contains("holder.itemView.setEnabled(available);"));
        assertTrue(adapter.contains("holder.itemView.setClickable(available);"));
        assertTrue(adapter.contains(
                "available ? view -> listener.onToolSelected(definition.getId()) : null"
        ));
        assertTrue(adapter.contains("DISABLED_ICON_ALPHA = 0.58f"));
        assertTrue(adapter.contains("R.string.tool_smart_scan_description"));
        assertTrue(adapter.contains("R.string.tool_document_viewer_description"));
        assertTrue(adapter.contains("R.string.tool_title_with_description"));
        assertTrue(adapter.contains(
                "R.string.catalog_tool_coming_soon_content_description"
        ));
        assertFalse(adapter.contains("text_tool_description"));
        assertFalse(adapter.contains("holder.description"));

        Document titleColors = parse(repositoryRoot().resolve(
                "app/src/main/res/color/catalog_tool_title.xml"
        ));
        NodeList items = titleColors.getElementsByTagName("item");
        boolean hasDisabledVariant = false;
        boolean hasActiveColor = false;
        for (int index = 0; index < items.getLength(); index++) {
            Element item = (Element) items.item(index);
            if ("false".equals(item.getAttributeNS(ANDROID, "state_enabled"))
                    && "?attr/colorOnSurfaceVariant".equals(
                    item.getAttributeNS(ANDROID, "color")
            )) {
                hasDisabledVariant = true;
            }
            if (!item.hasAttributeNS(ANDROID, "state_enabled")
                    && "?attr/colorOnSurface".equals(
                    item.getAttributeNS(ANDROID, "color")
            )) {
                hasActiveColor = true;
            }
        }
        assertTrue(hasDisabledVariant);
        assertTrue(hasActiveColor);

        Document item = parse(repositoryRoot().resolve(
                "app/src/main/res/layout/item_catalog_tool.xml"
        ));
        assertEquals("no", byId(item, "@+id/image_tool_icon")
                .getAttributeNS(ANDROID, "importantForAccessibility"));
        assertEquals("no", byId(item, "@+id/text_tool_title")
                .getAttributeNS(ANDROID, "importantForAccessibility"));
        assertTrue(dimensionDp("tool_icon_size") >= dimensionDp("touch_target"));
    }

    @Test
    public void catalogTitlesAndDescriptionsExistInRussianAndEnglish() throws Exception {
        String[] titleKeys = {
                "tool_image_to_pdf",
                "tool_smart_scan",
                "tool_docx_to_pdf",
                "tool_ppt_to_pdf",
                "tool_pdf_to_jpg",
                "tool_pdf_to_word",
                "tool_pdf_to_ppt",
                "tool_id_scan",
                "tool_import_pdf",
                "tool_print_pdf",
                "tool_document_viewer",
                "tool_merge_pdf",
                "tool_compress_pdf",
                "tool_draw_on_pdf",
                "tool_add_text",
                "tool_sign_pdf",
                "tool_lock_pdf",
                "tool_unlock_pdf"
        };
        for (String key : titleKeys) {
            assertFalse(readStringFromValues("values", key).trim().isEmpty());
            assertFalse(readStringFromValues("values-en", key).trim().isEmpty());
        }

        assertEquals("Изображение в PDF",
                readStringFromValues("values", "tool_image_to_pdf"));
        assertEquals("Image to PDF",
                readStringFromValues("values-en", "tool_image_to_pdf"));
        assertEquals("Сканировать документ",
                readStringFromValues("values", "tool_smart_scan"));
        assertEquals("Scan document",
                readStringFromValues("values-en", "tool_smart_scan"));
        assertEquals("Просмотр файлов",
                readStringFromValues("values", "tool_document_viewer"));
        assertEquals("File viewer",
                readStringFromValues("values-en", "tool_document_viewer"));
        assertEquals("Сфотографируйте страницы и создайте PDF",
                readStringFromValues("values", "tool_smart_scan_description"));
        assertEquals("PDF, DOCX, XLSX и изображения",
                readStringFromValues("values", "tool_document_viewer_description"));
        assertEquals("%1$s. Скоро", readStringFromValues(
                "values",
                "catalog_tool_coming_soon_content_description"
        ));
        assertEquals("%1$s. Coming soon", readStringFromValues(
                "values-en",
                "catalog_tool_coming_soon_content_description"
        ));
    }

    @Test
    public void sectionOrderRemainsCreatePopularEditSecurity() throws Exception {
        String fragment = Files.readString(
                repositoryRoot().resolve(
                        "app/src/main/java/com/desperadoboi/imagetopdf/ui/tools/"
                                + "AllToolsFragment.java"
                ),
                StandardCharsets.UTF_8
        );
        int create = fragment.indexOf("R.string.tool_category_create_convert");
        int popular = fragment.indexOf("R.string.tool_category_popular");
        int edit = fragment.indexOf("R.string.tool_category_edit");
        int security = fragment.indexOf("R.string.tool_category_security");
        assertTrue(create >= 0);
        assertTrue(create < popular);
        assertTrue(popular < edit);
        assertTrue(edit < security);
    }

    private static Element byId(Document document, String id) {
        NodeList elements = document.getElementsByTagName("*");
        for (int index = 0; index < elements.getLength(); index++) {
            Element element = (Element) elements.item(index);
            if (id.equals(element.getAttributeNS(ANDROID, "id"))) return element;
        }
        throw new AssertionError("Missing view " + id);
    }

    private static boolean hasElementWithId(Document document, String id) {
        NodeList elements = document.getElementsByTagName("*");
        for (int index = 0; index < elements.getLength(); index++) {
            Element element = (Element) elements.item(index);
            if (id.equals(element.getAttributeNS(ANDROID, "id"))) return true;
        }
        return false;
    }

    private static boolean hasDimension(String name) throws Exception {
        for (String file : new String[]{"dimens.xml", "all_tools_dimens.xml"}) {
            Document document = parse(repositoryRoot().resolve(
                    "app/src/main/res/values/" + file
            ));
            NodeList dimensions = document.getElementsByTagName("dimen");
            for (int index = 0; index < dimensions.getLength(); index++) {
                Element dimension = (Element) dimensions.item(index);
                if (name.equals(dimension.getAttribute("name"))) return true;
            }
        }
        return false;
    }

    private static int dimensionDp(String name) throws Exception {
        return Integer.parseInt(dimensionValue(name).replace("dp", ""));
    }

    private static int dimensionSp(String name) throws Exception {
        return Integer.parseInt(dimensionValue(name).replace("sp", ""));
    }

    private static String dimensionValue(String name) throws Exception {
        for (String file : new String[]{"dimens.xml", "all_tools_dimens.xml"}) {
            Document document = parse(repositoryRoot().resolve(
                    "app/src/main/res/values/" + file
            ));
            NodeList dimensions = document.getElementsByTagName("dimen");
            for (int index = 0; index < dimensions.getLength(); index++) {
                Element dimension = (Element) dimensions.item(index);
                if (name.equals(dimension.getAttribute("name"))) {
                    return dimension.getTextContent();
                }
            }
        }
        throw new AssertionError("Missing dimension " + name);
    }

    private static String readStringFromValues(String directory, String name) throws Exception {
        Path values = repositoryRoot().resolve("app/src/main/res/" + directory);
        try (DirectoryStream<Path> files = Files.newDirectoryStream(values, "*.xml")) {
            for (Path file : files) {
                Document document = parse(file);
                NodeList strings = document.getElementsByTagName("string");
                for (int index = 0; index < strings.getLength(); index++) {
                    Element string = (Element) strings.item(index);
                    if (name.equals(string.getAttribute("name"))) {
                        return string.getTextContent();
                    }
                }
            }
        }
        throw new AssertionError(directory + " is missing " + name);
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
