package com.desperadoboi.imagetopdf.ui.smartscan;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class SmartScanResponsiveContractTest {
    private static final String ANDROID = "http://schemas.android.com/apk/res/android";
    private static final String APP = "http://schemas.android.com/apk/res-auto";
    private static final String MATERIAL_BUTTON =
            "com.google.android.material.button.MaterialButton";

    @Test public void shutterRowKeepsTheShutterCenteredAndUsesFlexibleSideAreas()
            throws Exception {
        Document layout = parse("app/src/main/res/layout/fragment_smart_scan.xml");
        Element bottomBar = findById(layout, "layout_scan_bottom_bar");
        Element gallery = findById(layout, "button_scan_gallery");
        Element shutter = findById(layout, "layout_scan_shutter");
        Element done = findById(layout, "button_scan_done");

        assertEquals("@dimen/scan_shutter_size", android(bottomBar, "layout_height"));
        assertEquals(MATERIAL_BUTTON, gallery.getTagName());
        assertEquals("0dp", android(gallery, "layout_width"));
        assertEquals("@dimen/touch_target", android(gallery, "minHeight"));
        assertEquals("?attr/textAppearanceLabelMedium",
                android(gallery, "textAppearance"));
        assertEquals("@id/layout_scan_shutter", app(gallery, "layout_constraintEnd_toStartOf"));
        assertEquals("parent", app(gallery, "layout_constraintStart_toStartOf"));

        assertEquals("@dimen/scan_shutter_size", android(shutter, "layout_width"));
        assertEquals("parent", app(shutter, "layout_constraintStart_toStartOf"));
        assertEquals("parent", app(shutter, "layout_constraintEnd_toEndOf"));

        assertEquals(MATERIAL_BUTTON, done.getTagName());
        assertEquals("0dp", android(done, "layout_width"));
        assertEquals("@dimen/touch_target", android(done, "minHeight"));
        assertEquals("2", android(done, "maxLines"));
        assertEquals("?attr/textAppearanceLabelMedium",
                android(done, "textAppearance"));
        assertEquals("@id/layout_scan_shutter", app(done, "layout_constraintStart_toEndOf"));
        assertEquals("parent", app(done, "layout_constraintEnd_toEndOf"));

        String dimensions = read("app/src/main/res/values/dimens.xml");
        assertFalse(dimensions.contains("scan_bottom_side_width"));
        Document landscapeDimensions = parse("app/src/main/res/values-land/dimens.xml");
        assertEquals("4dp", dimen(landscapeDimensions, "scan_controls_padding"));
        assertEquals("2dp", dimen(landscapeDimensions, "scan_controls_spacing"));
        assertEquals("64dp", dimen(landscapeDimensions, "scan_shutter_size"));
        assertNotNull(findById(layout, "text_scan_mode"));
        String fragment = read(
                "app/src/main/java/com/desperadoboi/imagetopdf/ui/smartscan/"
                        + "SmartScanFragment.java"
        );
        assertTrue(fragment.contains(
                "configuration.orientation == Configuration.ORIENTATION_LANDSCAPE"
        ));
        assertTrue(fragment.contains("configuration.screenHeightDp < 480"));
        assertTrue(fragment.contains("R.id.text_scan_mode).setVisibility(View.GONE)"));
        assertTrue(fragment.contains("if (compactLandscapeControls)"));
        for (int widthDp : new int[]{320, 360, 393, 412}) {
            int flexibleSideWidth = (widthDp - (2 * 12) - 78) / 2 - 4;
            assertTrue(widthDp + "dp side target", flexibleSideWidth >= 48);
        }
    }

    @Test public void captureAndToolbarControlsKeepAtLeastFortyEightDpTargets()
            throws Exception {
        Document layout = parse("app/src/main/res/layout/fragment_smart_scan.xml");
        for (String id : Arrays.asList(
                "button_scan_back", "button_scan_torch", "button_scan_grid",
                "button_scan_more"
        )) {
            Element button = findById(layout, id);
            assertEquals("@dimen/touch_target", android(button, "layout_width"));
            assertEquals("@dimen/touch_target", android(button, "layout_height"));
            assertFalse(android(button, "contentDescription").isEmpty());
        }
        Element shutter = findById(layout, "button_scan_shutter");
        assertEquals("match_parent", android(shutter, "layout_width"));
        assertEquals("match_parent", android(shutter, "layout_height"));
        assertEquals("@string/smart_scan_capture_content_description",
                android(shutter, "contentDescription"));
    }

    @Test public void doneReplacesTheSeparateCounterAndUsesAndroidPlurals()
            throws Exception {
        Document layout = parse("app/src/main/res/layout/fragment_smart_scan.xml");
        String fragment = read(
                "app/src/main/java/com/desperadoboi/imagetopdf/ui/smartscan/"
                        + "SmartScanFragment.java"
        );

        assertNull(findOptionalById(layout, "text_scan_page_count"));
        assertTrue(fragment.contains(
                "doneButton.setVisibility(pageCount > 0 ? View.VISIBLE : View.GONE)"
        ));
        assertTrue(fragment.contains("R.string.smart_scan_done, pageCount"));
        assertTrue(fragment.contains(
                "getQuantityString(\n                    "
                        + "R.plurals.smart_scan_done_content_description"
        ));
        assertTrue(fragment.contains("doneButton.setContentDescription(null)"));
        assertFalse(fragment.contains("pageCountText"));
        assertFalse(fragment.contains("SmartScanDoneFormatter"));
        assertFalse(fragment.contains("PageCountFormatter"));
    }

    @Test public void doneLabelsAndDescriptionsCoverRussianAndEnglishCounts()
            throws Exception {
        int[] counts = {1, 2, 5, 11, 21};
        String[] russianDescriptions = {
                "Завершить сканирование, 1 страница",
                "Завершить сканирование, 2 страницы",
                "Завершить сканирование, 5 страниц",
                "Завершить сканирование, 11 страниц",
                "Завершить сканирование, 21 страница"
        };
        String[] englishDescriptions = {
                "Finish scanning, 1 page",
                "Finish scanning, 2 pages",
                "Finish scanning, 5 pages",
                "Finish scanning, 11 pages",
                "Finish scanning, 21 pages"
        };
        Document russian = parse("app/src/main/res/values/strings.xml");
        Document english = parse("app/src/main/res/values-en/smart_scan_strings.xml");
        String russianDone = string(russian, "smart_scan_done");
        String englishDone = string(english, "smart_scan_done");
        Map<String, String> russianPlural = plural(russian,
                "smart_scan_done_content_description");
        Map<String, String> englishPlural = plural(english,
                "smart_scan_done_content_description");

        for (int index = 0; index < counts.length; index++) {
            int count = counts[index];
            assertEquals("Готово · " + count,
                    String.format(new Locale("ru"), russianDone, count));
            assertEquals("Done · " + count,
                    String.format(Locale.ENGLISH, englishDone, count));
            assertEquals(russianDescriptions[index], String.format(
                    new Locale("ru"),
                    russianPlural.get(russianQuantity(count)),
                    count
            ));
            assertEquals(englishDescriptions[index], String.format(
                    Locale.ENGLISH,
                    englishPlural.get(count == 1 ? "one" : "other"),
                    count
            ));
        }
    }

    @Test public void scanReviewUsesResponsiveMaterialButtonGridWithoutTinyAutosize()
            throws Exception {
        Document layout = parse("app/src/main/res/layout/fragment_scan_review.xml");
        Element toolbar = findById(layout, "layout_scan_review_tools");
        assertEquals("GridLayout", toolbar.getTagName());
        assertEquals("@integer/scan_review_column_count", android(toolbar, "columnCount"));
        assertEquals("2", integer(
                parse("app/src/main/res/values/scan_responsive.xml"),
                "scan_review_column_count"
        ));
        assertEquals("4", integer(
                parse("app/src/main/res/values-w600dp/scan_responsive.xml"),
                "scan_review_column_count"
        ));

        for (String id : Arrays.asList(
                "button_scan_review_retake", "button_scan_review_rotate",
                "button_scan_review_auto", "button_scan_review_original"
        )) {
            Element button = findById(layout, id);
            assertEquals(MATERIAL_BUTTON, button.getTagName());
            assertEquals("0dp", android(button, "layout_width"));
            assertEquals("wrap_content", android(button, "layout_height"));
            assertEquals("@dimen/touch_target", android(button, "minHeight"));
            assertEquals("2", android(button, "maxLines"));
            assertEquals("1", android(button, "layout_columnWeight"));
            assertEquals("?attr/textAppearanceLabelMedium",
                    android(button, "textAppearance"));
            assertFalse(app(button, "icon").isEmpty());
            assertTrue(android(button, "contentDescription").isEmpty());
        }

        String source = read("app/src/main/res/layout/fragment_scan_review.xml");
        assertFalse(source.contains("autoSize"));
        assertFalse(source.contains("9sp"));
    }

    @Test public void sharedReviewKeepsDynamicIdCardActionSelectionAndHiddenSemantics()
            throws Exception {
        String fragment = read(
                "app/src/main/java/com/desperadoboi/imagetopdf/ui/smartscan/"
                        + "ScanReviewFragment.java"
        );
        assertTrue(fragment.contains("private MaterialButton autoButton"));
        assertTrue(fragment.contains("private MaterialButton originalButton"));
        assertTrue(fragment.contains("autoButton.setText(R.string.id_card_find_edges)"));
        assertTrue(fragment.contains("autoButton.setSelected(!page.isOriginal())"));
        assertTrue(fragment.contains("originalButton.setSelected(page.isOriginal())"));
        assertTrue(fragment.contains("originalButton.setVisibility(View.GONE)"));
        assertFalse(fragment.contains(
                "button_scan_review_retake).setContentDescription"
        ));

        assertEquals("Найти карту", string(
                parse("app/src/main/res/values/id_card_strings.xml"),
                "id_card_find_edges"
        ));
        assertEquals("Find card", string(
                parse("app/src/main/res/values-en/id_card_strings.xml"),
                "id_card_find_edges"
        ));
        assertTrue(read("app/src/main/res/color/scan_review_action_container.xml")
                .contains("android:state_selected=\"true\""));
    }

    private static String russianQuantity(int count) {
        int mod100 = count % 100;
        if (mod100 >= 11 && mod100 <= 14) return "many";
        int mod10 = count % 10;
        if (mod10 == 1) return "one";
        if (mod10 >= 2 && mod10 <= 4) return "few";
        return "many";
    }

    private static String android(Element element, String name) {
        return element.getAttributeNS(ANDROID, name);
    }

    private static String app(Element element, String name) {
        return element.getAttributeNS(APP, name);
    }

    private static String integer(Document document, String name) {
        NodeList nodes = document.getElementsByTagName("integer");
        for (int index = 0; index < nodes.getLength(); index++) {
            Element element = (Element) nodes.item(index);
            if (name.equals(element.getAttribute("name"))) return element.getTextContent();
        }
        throw new AssertionError("Missing integer: " + name);
    }

    private static String dimen(Document document, String name) {
        NodeList nodes = document.getElementsByTagName("dimen");
        for (int index = 0; index < nodes.getLength(); index++) {
            Element element = (Element) nodes.item(index);
            if (name.equals(element.getAttribute("name"))) return element.getTextContent();
        }
        throw new AssertionError("Missing dimen: " + name);
    }

    private static String string(Document document, String name) {
        NodeList nodes = document.getElementsByTagName("string");
        for (int index = 0; index < nodes.getLength(); index++) {
            Element element = (Element) nodes.item(index);
            if (name.equals(element.getAttribute("name"))) return element.getTextContent();
        }
        throw new AssertionError("Missing string: " + name);
    }

    private static Map<String, String> plural(Document document, String name) {
        NodeList plurals = document.getElementsByTagName("plurals");
        for (int index = 0; index < plurals.getLength(); index++) {
            Element element = (Element) plurals.item(index);
            if (!name.equals(element.getAttribute("name"))) continue;
            Map<String, String> result = new HashMap<>();
            NodeList children = element.getChildNodes();
            for (int childIndex = 0; childIndex < children.getLength(); childIndex++) {
                Node child = children.item(childIndex);
                if (child instanceof Element && "item".equals(child.getNodeName())) {
                    Element item = (Element) child;
                    result.put(item.getAttribute("quantity"), item.getTextContent());
                }
            }
            return result;
        }
        throw new AssertionError("Missing plurals: " + name);
    }

    private static Element findById(Document document, String id) {
        Element element = findOptionalById(document, id);
        assertNotNull("Missing view: " + id, element);
        return element;
    }

    private static Element findOptionalById(Document document, String id) {
        NodeList nodes = document.getElementsByTagName("*");
        for (int index = 0; index < nodes.getLength(); index++) {
            Element element = (Element) nodes.item(index);
            String value = android(element, "id");
            if (("@+id/" + id).equals(value) || ("@id/" + id).equals(value)) {
                return element;
            }
        }
        return null;
    }

    private static Document parse(String relativePath) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "");
        factory.setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "");
        return factory.newDocumentBuilder().parse(repositoryRoot().resolve(relativePath).toFile());
    }

    private static String read(String relativePath) throws Exception {
        return Files.readString(repositoryRoot().resolve(relativePath), StandardCharsets.UTF_8);
    }

    private static Path repositoryRoot() {
        Path current = Paths.get("").toAbsolutePath().normalize();
        while (current != null && !Files.exists(current.resolve("settings.gradle.kts"))) {
            current = current.getParent();
        }
        if (current == null) throw new IllegalStateException("Repository root not found");
        return current;
    }
}
