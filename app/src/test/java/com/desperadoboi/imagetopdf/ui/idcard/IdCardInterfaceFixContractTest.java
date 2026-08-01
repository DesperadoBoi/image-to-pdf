package com.desperadoboi.imagetopdf.ui.idcard;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class IdCardInterfaceFixContractTest {
    private static final String ANDROID = "http://schemas.android.com/apk/res/android";
    private static final Pattern PLACEHOLDER = Pattern.compile("%(\\d+)\\$[a-zA-Z]");

    @Test public void actionRowsAdaptToMeasuredCardWidthAndKeepTextHeightFlexible()
            throws Exception {
        Document side = parse("app/src/main/res/layout/view_id_card_side.xml");
        String responsive = read(
                "app/src/main/java/com/desperadoboi/imagetopdf/ui/idcard/"
                        + "ResponsiveActionRow.java"
        );

        for (String id : Arrays.asList(
                "layout_id_card_empty_actions",
                "layout_id_card_ready_source_actions",
                "layout_id_card_ready_edit_actions"
        )) {
            assertEquals(
                    "com.desperadoboi.imagetopdf.ui.idcard.ResponsiveActionRow",
                    findById(side, id).getTagName()
            );
        }
        for (String id : Arrays.asList(
                "button_id_card_camera", "button_id_card_gallery",
                "button_id_card_replace", "button_id_card_retake",
                "button_id_card_rotate", "button_id_card_correct",
                "button_id_card_delete"
        )) {
            Element button = findById(side, id);
            assertEquals("wrap_content", button.getAttributeNS(ANDROID, "layout_height"));
            assertEquals("@dimen/touch_target", button.getAttributeNS(ANDROID, "minHeight"));
            assertEquals("2", button.getAttributeNS(ANDROID, "maxLines"));
            assertEquals("none", button.getAttributeNS(ANDROID, "hyphenationFrequency"));
        }
        assertTrue(responsive.contains("MeasureSpec.getSize(widthMeasureSpec)"));
        assertTrue(responsive.contains("measureRequiredHorizontalWidth"));
        assertTrue(responsive.contains("int requestedOrientation = stacked ? VERTICAL : HORIZONTAL"));
        assertFalse(responsive.contains("getResources().getConfiguration().screenWidthDp"));
        assertTrue(read("app/src/main/res/values/id_card_strings.xml")
                .contains("name=\"id_card_action_take_photo\">Камера</string>"));
    }

    @Test public void compactAndLandscapeCardWidthContractsStackActions() {
        assertTrue(ResponsiveActionRow.shouldStack(288, 450)); // 320dp, fontScale 1.5
        assertTrue(ResponsiveActionRow.shouldStack(328, 420)); // 360dp, fontScale 1.3
        assertTrue(ResponsiveActionRow.shouldStack(352, 380)); // 384dp, RU ready actions
        assertTrue(ResponsiveActionRow.shouldStack(300, 380)); // landscape half-card
        assertFalse(ResponsiveActionRow.shouldStack(560, 380));
    }

    @Test public void portraitAndLandscapeRemainScrollableWithoutFixedCardHeight()
            throws Exception {
        for (String path : Arrays.asList(
                "app/src/main/res/layout/fragment_id_card_scan.xml",
                "app/src/main/res/layout-land/fragment_id_card_scan.xml"
        )) {
            Document layout = parse(path);
            assertNotNull(findById(layout, "scroll_id_card"));
            assertEquals("wrap_content", findById(layout, "card_id_front")
                    .getAttributeNS(ANDROID, "layout_height"));
            assertEquals("wrap_content", findById(layout, "card_id_back")
                    .getAttributeNS(ANDROID, "layout_height"));
        }
    }

    @Test public void idCardScreenHandlesImeLocallyAndScrollsFocusedWatermark()
            throws Exception {
        String fragment = read(
                "app/src/main/java/com/desperadoboi/imagetopdf/ui/idcard/"
                        + "IdCardScanFragment.java"
        );
        String activity = read(
                "app/src/main/java/com/desperadoboi/imagetopdf/MainActivity.java"
        );

        assertTrue(fragment.contains("WindowInsetsCompat.Type.ime()"));
        assertTrue(fragment.contains("WindowInsetsCompat.Type.systemBars()"));
        assertTrue(fragment.contains("IdCardImeInsetPolicy.bottomPadding"));
        assertTrue(fragment.contains("IdCardImeInsetPolicy.scrollDeltaToReveal"));
        assertTrue(fragment.contains("watermarkInput.setOnFocusChangeListener"));
        assertTrue(fragment.contains("requestRectangleOnScreen(visibleRequest, true)"));
        assertTrue(fragment.contains("scrollView.scrollBy(0, delta)"));
        assertTrue(fragment.contains("R.string.id_card_watermark_default"));
        String options = read("app/src/main/res/layout/view_id_card_export_options.xml");
        assertTrue(options.contains("android:imeOptions=\"flagNoExtractUi|actionDone\""));
        assertTrue(activity.contains("EdgeToEdge.enable(this)"));
        assertFalse(activity.contains("WindowInsetsCompat.Type.ime()"));
    }

    @Test public void readyPreviewUsesStableIdCardRequestInsteadOfTransientPageIdentity()
            throws Exception {
        String fragment = read(
                "app/src/main/java/com/desperadoboi/imagetopdf/ui/idcard/"
                        + "IdCardScanFragment.java"
        );
        String loader = read(
                "app/src/main/java/com/desperadoboi/imagetopdf/image/"
                        + "PreviewImageLoader.java"
        );

        assertTrue(fragment.contains("new IdCardPreviewRequest("));
        assertTrue(fragment.contains("previewRequests.isCurrent(side, loadedKey)"));
        assertTrue(fragment.contains("views.previewContainer.getWidth()"));
        assertTrue(fragment.contains("views.previewContainer.post("));
        assertFalse(fragment.contains("views.preview.getWidth() <= 0"));
        assertTrue(fragment.contains("views.preview.setImageDrawable(null)"));
        assertFalse(fragment.contains("image.toPageItem()"));
        assertTrue(loader.contains("String requestKey"));
    }

    @Test public void everySharedStringOnIdCardEndToEndPathHasEnglishValue()
            throws Exception {
        Map<String, String> english = loadStrings("values-en");
        for (String name : reachableSharedStrings()) {
            assertTrue("Missing EN string: " + name, english.containsKey(name));
            assertFalse("Blank EN string: " + name, english.get(name).trim().isEmpty());
        }
    }

    @Test public void oneSidePermissionReviewAndResultPathsAreCompletelyEnglish()
            throws Exception {
        Map<String, String> english = loadStrings("values-en");
        assertEnglish(english,
                "id_card_one_side_title", "id_card_one_side_message",
                "id_card_one_side_confirm", "action_cancel");
        assertEnglish(english,
                "id_card_camera_permission_message", "id_card_camera_permission_rationale",
                "smart_scan_permission_allow", "smart_scan_gallery",
                "smart_scan_open_settings");
        assertEnglish(english,
                "id_card_correct_front_title", "id_card_correct_back_title",
                "scan_review_retake", "scan_review_rotate", "id_card_find_edges",
                "id_card_apply_correction", "scan_review_load_error");
        assertEnglish(english,
                "pdf_success_banner_title", "action_share_pdf", "action_open_pdf_short",
                "action_return_to_pdf_pages", "action_new_pdf_document",
                "pdf_result_location", "pdf_result_unavailable");
        assertEnglish(english, "id_card_watermark_default");
    }

    @Test public void reachableFormattingPlaceholdersMatchRussianResources()
            throws Exception {
        Map<String, String> russian = loadStrings("values");
        Map<String, String> english = loadStrings("values-en");

        for (String name : reachableSharedStrings()) {
            assertEquals(
                    "Placeholder mismatch: " + name,
                    placeholders(russian.get(name)),
                    placeholders(english.get(name))
            );
        }
    }

    @Test public void idCardUiCodeAndLayoutsContainNoHardcodedRussianCopy()
            throws Exception {
        Pattern cyrillic = Pattern.compile("[А-Яа-яЁё]");
        for (String path : Arrays.asList(
                "app/src/main/java/com/desperadoboi/imagetopdf/ui/idcard/IdCardScanFragment.java",
                "app/src/main/java/com/desperadoboi/imagetopdf/ui/idcard/IdCardScanViewModel.java",
                "app/src/main/java/com/desperadoboi/imagetopdf/ui/idcard/IdCardExportOptions.java",
                "app/src/main/java/com/desperadoboi/imagetopdf/ui/idcard/ResponsiveActionRow.java",
                "app/src/main/res/layout/fragment_id_card_scan.xml",
                "app/src/main/res/layout-land/fragment_id_card_scan.xml",
                "app/src/main/res/layout/view_id_card_side.xml",
                "app/src/main/res/layout/view_id_card_export_options.xml"
        )) {
            assertFalse(path, cyrillic.matcher(read(path)).find());
        }
    }

    private static List<String> reachableSharedStrings() {
        return Arrays.asList(
                "action_cancel", "smart_scan_torch_content_description",
                "smart_scan_gallery", "smart_scan_permission_allow",
                "smart_scan_open_settings", "scan_review_load_error",
                "scan_review_retake", "scan_review_rotate",
                "document_overlay_content_description",
                "scan_review_corner_overlay_content_description",
                "pdf_result_preview_content_description",
                "action_share_pdf_content_description", "action_share_pdf",
                "action_open_pdf_content_description", "action_open_pdf_short",
                "action_return_to_pdf_pages_content_description",
                "action_return_to_pdf_pages",
                "action_new_pdf_document_content_description",
                "action_new_pdf_document", "pdf_success_banner_content_description",
                "pdf_success_banner_title", "pdf_success_banner_announcement",
                "pdf_page_word_one", "pdf_page_word_few", "pdf_page_word_many",
                "pdf_result_unavailable", "pdf_result_unknown_name",
                "pdf_result_page_badge", "pdf_result_size_unknown",
                "pdf_result_summary", "pdf_location_selected_folder",
                "pdf_result_location_unknown", "pdf_result_location",
                "pdf_share_chooser_title", "status_pdf_share_error",
                "status_pdf_open_app_not_found", "status_pdf_open_error",
                "pdf_location_downloads", "pdf_location_documents"
        );
    }

    private static void assertEnglish(Map<String, String> strings, String... names) {
        Pattern cyrillic = Pattern.compile("[А-Яа-яЁё]");
        for (String name : names) {
            assertTrue("Missing EN string: " + name, strings.containsKey(name));
            assertFalse(name, cyrillic.matcher(strings.get(name)).find());
        }
    }

    private static Set<String> placeholders(String value) {
        Set<String> result = new HashSet<>();
        Matcher matcher = PLACEHOLDER.matcher(value == null ? "" : value);
        while (matcher.find()) result.add(matcher.group());
        return result;
    }

    private static Map<String, String> loadStrings(String folder) throws Exception {
        Map<String, String> result = new HashMap<>();
        Path values = repositoryRoot().resolve("app/src/main/res").resolve(folder);
        try (DirectoryStream<Path> files = Files.newDirectoryStream(values, "*.xml")) {
            for (Path path : files) {
                Document document = parse(path);
                NodeList strings = document.getElementsByTagName("string");
                for (int index = 0; index < strings.getLength(); index++) {
                    Element string = (Element) strings.item(index);
                    result.put(string.getAttribute("name"), string.getTextContent());
                }
            }
        }
        return result;
    }

    private static Element findById(Document document, String id) {
        NodeList nodes = document.getElementsByTagName("*");
        for (int index = 0; index < nodes.getLength(); index++) {
            Element element = (Element) nodes.item(index);
            String value = element.getAttributeNS(ANDROID, "id");
            if (("@+id/" + id).equals(value) || ("@id/" + id).equals(value)) {
                return element;
            }
        }
        throw new AssertionError("Missing view: " + id);
    }

    private static Document parse(String relativePath) throws Exception {
        return parse(repositoryRoot().resolve(relativePath));
    }

    private static Document parse(Path path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "");
        factory.setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "");
        return factory.newDocumentBuilder().parse(path.toFile());
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
