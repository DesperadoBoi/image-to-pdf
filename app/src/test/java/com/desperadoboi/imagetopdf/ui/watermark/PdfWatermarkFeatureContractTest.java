package com.desperadoboi.imagetopdf.ui.watermark;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class PdfWatermarkFeatureContractTest {
    private static final String ANDROID = "http://schemas.android.com/apk/res/android";

    @Test public void catalogItemIsAvailableDescribedAndNotOnHome() throws Exception {
        String catalog = read("app/src/main/java/com/desperadoboi/imagetopdf/ui/tools/ToolCatalog.java");
        String adapter = read("app/src/main/java/com/desperadoboi/imagetopdf/ui/tools/AllToolsAdapter.java");
        String homeLayout = read("app/src/main/res/layout/fragment_home.xml");

        assertTrue(catalog.contains("tool(ToolId.PDF_WATERMARK"));
        assertTrue(catalog.contains("R.drawable.ic_tool_pdf_watermark, ToolCategory.EDIT"));
        assertTrue(catalog.contains("ToolAvailability.AVAILABLE, false"));
        assertTrue(adapter.contains("R.string.tool_pdf_watermark_description"));
        assertFalse(homeLayout.contains("pdf_watermark"));
    }

    @Test public void russianAndEnglishCopyMatchesProductContract() throws Exception {
        Document ru = parse("app/src/main/res/values/pdf_watermark_strings.xml");
        Document en = parse("app/src/main/res/values-en/pdf_watermark_strings.xml");

        assertString(ru, "tool_pdf_watermark", "Водяной знак");
        assertString(en, "tool_pdf_watermark", "Watermark");
        assertString(ru, "tool_pdf_watermark_description", "Добавить водяной знак в PDF");
        assertString(en, "tool_pdf_watermark_description", "Add a watermark to a PDF");
        assertString(ru, "pdf_watermark_default_text", "КОПИЯ");
        assertString(en, "pdf_watermark_default_text", "COPY");
        for (String key : Arrays.asList(
                "pdf_watermark_error_open", "pdf_watermark_error_corrupted",
                "pdf_watermark_error_encrypted", "pdf_watermark_error_too_large",
                "pdf_watermark_error_render", "pdf_watermark_error_memory",
                "pdf_watermark_error_create", "pdf_watermark_error_save",
                "pdf_watermark_error_source_unavailable", "pdf_watermark_error_cancelled"
        )) {
            assertStringPresent(ru, key);
            assertStringPresent(en, key);
        }
    }

    @Test public void screenContainsPreviewLiveOverlayAndAllRequiredControls() throws Exception {
        Document layout = parse("app/src/main/res/layout/fragment_pdf_watermark.xml");
        for (String id : Arrays.asList(
                "card_pdf_watermark_preview", "image_pdf_watermark_preview",
                "overlay_pdf_watermark_preview", "button_pdf_watermark_previous",
                "button_pdf_watermark_retry_preview",
                "text_pdf_watermark_page_counter", "button_pdf_watermark_next",
                "input_pdf_watermark_text", "group_pdf_watermark_presets",
                "button_pdf_watermark_single", "button_pdf_watermark_tiled",
                "slider_pdf_watermark_opacity", "slider_pdf_watermark_size",
                "slider_pdf_watermark_rotation", "group_pdf_watermark_colors",
                "grid_pdf_watermark_position", "group_pdf_watermark_pages",
                "input_pdf_watermark_range", "button_pdf_watermark_create"
        )) {
            assertNotNull(id, findById(layout, id));
        }
        assertEquals("yes", findById(layout, "card_pdf_watermark_preview")
                .getAttributeNS(ANDROID, "importantForAccessibility"));
        assertEquals("no", findById(layout, "image_pdf_watermark_preview")
                .getAttributeNS(ANDROID, "importantForAccessibility"));
        assertEquals("no", findById(layout, "overlay_pdf_watermark_preview")
                .getAttributeNS(ANDROID, "importantForAccessibility"));
    }

    @Test public void textAndSliderBoundsAreDeclaredInLayout() throws Exception {
        Document layout = parse("app/src/main/res/layout/fragment_pdf_watermark.xml");
        assertEquals("60", findById(layout, "input_pdf_watermark_text")
                .getAttributeNS(ANDROID, "maxLength"));
        assertSlider(layout, "slider_pdf_watermark_opacity", "10", "80", "30");
        assertSlider(layout, "slider_pdf_watermark_size", "18", "96", "48");
        assertSlider(layout, "slider_pdf_watermark_rotation", "-60", "60", "-45");
    }

    @Test public void navigationPositionAndMainActionsMeetMinimumTouchTarget() throws Exception {
        Document layout = parse("app/src/main/res/layout/fragment_pdf_watermark.xml");
        for (String id : Arrays.asList(
                "button_pdf_watermark_previous", "button_pdf_watermark_next",
                "button_position_top_left", "button_position_top_center",
                "button_position_top_right", "button_position_center_left",
                "button_position_center", "button_position_center_right",
                "button_position_bottom_left", "button_position_bottom_center",
                "button_position_bottom_right"
        )) {
            assertEquals(id, "@dimen/touch_target",
                    findById(layout, id).getAttributeNS(ANDROID, "layout_height"));
        }
        assertEquals("@dimen/touch_target", findById(layout, "chip_pdf_watermark_copy")
                .getAttributeNS(ANDROID, "minHeight"));
        assertEquals("@dimen/touch_target",
                findById(layout, "button_pdf_watermark_retry_preview")
                        .getAttributeNS(ANDROID, "minHeight"));
        assertEquals("@dimen/primary_button_height",
                findById(layout, "button_pdf_watermark_create")
                        .getAttributeNS(ANDROID, "minHeight"));
    }

    @Test public void screenIsScrollableAndLandscapeUsesAdaptivePreviewDimension() throws Exception {
        Document layout = parse("app/src/main/res/layout/fragment_pdf_watermark.xml");
        assertNotNull(findById(layout, "scroll_pdf_watermark"));
        Element pageCounter = findById(layout, "text_pdf_watermark_page_counter");
        assertEquals("wrap_content", pageCounter.getAttributeNS(ANDROID, "layout_width"));
        assertEquals("96dp", pageCounter.getAttributeNS(ANDROID, "minWidth"));
        assertEquals("@dimen/pdf_watermark_preview_height",
                findById(layout, "card_pdf_watermark_preview")
                        .getAttributeNS(ANDROID, "layout_height"));
        String portrait = read("app/src/main/res/values/pdf_watermark_dimens.xml");
        String landscape = read("app/src/main/res/values-land/pdf_watermark_dimens.xml");
        assertTrue(portrait.contains("name=\"pdf_watermark_preview_height\">280dp"));
        assertTrue(landscape.contains("name=\"pdf_watermark_preview_height\">180dp"));
        assertFalse(read("app/src/main/res/layout/fragment_pdf_watermark.xml")
                .contains("android:layout_height=\"600dp\""));

        String fragment = read(
                "app/src/main/java/com/desperadoboi/imagetopdf/ui/watermark/PdfWatermarkFragment.java"
        );
        assertTrue(fragment.contains("scrollBasePaddingBottom + imeBottom"));
        assertTrue(fragment.contains("field.getLocationOnScreen(fieldLocation)"));
    }

    @Test public void safFlowAcceptsOnlyPdfAndUsesCreateDocument() throws Exception {
        String fragment = read("app/src/main/java/com/desperadoboi/imagetopdf/ui/watermark/PdfWatermarkFragment.java");
        assertTrue(fragment.contains("ActivityResultContracts.OpenDocument"));
        assertTrue(fragment.contains("new String[]{PDF_MIME_TYPE}"));
        assertTrue(fragment.contains("ActivityResultContracts.CreateDocument(PDF_MIME_TYPE)"));
        assertFalse(fragment.contains("\"*/*\""));
        assertTrue(fragment.contains("takePersistableUriPermission"));
    }

    @Test public void previewAndExportSharePainterAndDoNotRegenerateOnSliderTicks()
            throws Exception {
        String overlay = read("app/src/main/java/com/desperadoboi/imagetopdf/ui/watermark/WatermarkOverlayView.java");
        String generator = read("app/src/main/java/com/desperadoboi/imagetopdf/pdf/PdfWatermarkGenerator.java");
        String fragment = read("app/src/main/java/com/desperadoboi/imagetopdf/ui/watermark/PdfWatermarkFragment.java");
        assertTrue(overlay.contains("new WatermarkPainter()"));
        assertTrue(generator.contains("new WatermarkPainter()"));
        assertTrue(overlay.contains("painter.draw(canvas, pageWidthPoints, pageHeightPoints, options)"));
        assertTrue(generator.contains("watermarkPainter.draw(canvas, pageWidth, pageHeight, options)"));
        assertTrue(fragment.contains("viewModel.setOpacity(value / 100f)"));
        assertFalse(fragment.contains("setOpacity(value / 100f);\n            startExport"));
    }

    @Test public void stateUsesSavedStateWithoutBitmapOrSensitiveTextHistory() throws Exception {
        String viewModel = read("app/src/main/java/com/desperadoboi/imagetopdf/ui/watermark/PdfWatermarkViewModel.java");
        assertTrue(viewModel.contains("SavedStateHandle"));
        assertTrue(viewModel.contains("KEY_CURRENT_PAGE"));
        assertTrue(viewModel.contains("KEY_EXPORT_PHASE"));
        assertTrue(viewModel.contains("KEY_TEMP_OUTPUT"));
        assertFalse(viewModel.contains("android.graphics.Bitmap"));
        assertFalse(viewModel.toLowerCase(Locale.ROOT).contains("history"));
    }

    @Test public void watermarkFlowAddsNoNetworkStorageOrSensitiveLogging() throws Exception {
        String manifest = read("app/src/main/AndroidManifest.xml");
        String fragment = read("app/src/main/java/com/desperadoboi/imagetopdf/ui/watermark/PdfWatermarkFragment.java");
        String packageSource = fragment
                + read("app/src/main/java/com/desperadoboi/imagetopdf/ui/watermark/PdfWatermarkViewModel.java")
                + read("app/src/main/java/com/desperadoboi/imagetopdf/pdf/PdfWatermarkGenerator.java");
        assertFalse(manifest.contains("android.permission.INTERNET"));
        assertFalse(manifest.contains("android.permission.MANAGE_EXTERNAL_STORAGE"));
        assertFalse(packageSource.contains("READ_MEDIA"));
        assertFalse(packageSource.contains("READ_EXTERNAL_STORAGE"));
        assertFalse(packageSource.contains("android.util.Log"));
        assertFalse(packageSource.contains("Log."));
        assertFalse(packageSource.contains("Clipboard"));
        assertFalse(packageSource.contains("http://"));
        assertFalse(packageSource.contains("https://"));
    }

    @Test public void exportIsSequentialBoundedCancellableAndCleansResources() throws Exception {
        String generator = read("app/src/main/java/com/desperadoboi/imagetopdf/pdf/PdfWatermarkGenerator.java");
        assertTrue(generator.contains("MAX_BITMAP_PIXELS = 8_000_000L"));
        assertTrue(generator.contains("TARGET_DPI = 180"));
        assertTrue(generator.contains("for (int index = 0; index < expectedPageCount; index++)"));
        assertTrue(generator.contains("try (PdfRenderer.Page sourcePage"));
        assertTrue(generator.contains("bitmap.recycle()"));
        assertTrue(generator.contains("throwIfCancelled(cancellationToken)"));
        assertTrue(generator.contains("deleteTemporaryFile(temporaryFile)"));
        assertTrue(generator.contains("deletePartialOutput(outputUri)"));
        assertTrue(generator.contains("if (applyWatermark)"));
        assertFalse(generator.contains("List<Bitmap>"));
    }

    private static void assertSlider(
            Document layout, String id, String from, String to, String value
    ) {
        Element slider = findById(layout, id);
        assertEquals(from, slider.getAttributeNS(ANDROID, "valueFrom"));
        assertEquals(to, slider.getAttributeNS(ANDROID, "valueTo"));
        assertEquals(value, slider.getAttributeNS(ANDROID, "value"));
    }

    private static void assertString(Document document, String name, String expected) {
        NodeList strings = document.getElementsByTagName("string");
        for (int index = 0; index < strings.getLength(); index++) {
            Element element = (Element) strings.item(index);
            if (name.equals(element.getAttribute("name"))) {
                assertEquals(expected, element.getTextContent());
                return;
            }
        }
        throw new AssertionError("Missing string: " + name);
    }

    private static void assertStringPresent(Document document, String name) {
        NodeList strings = document.getElementsByTagName("string");
        for (int index = 0; index < strings.getLength(); index++) {
            if (name.equals(((Element) strings.item(index)).getAttribute("name"))) return;
        }
        throw new AssertionError("Missing string: " + name);
    }

    private static Element findById(Document document, String id) {
        NodeList nodes = document.getElementsByTagName("*");
        for (int index = 0; index < nodes.getLength(); index++) {
            Element element = (Element) nodes.item(index);
            String value = element.getAttributeNS(ANDROID, "id");
            if (("@+id/" + id).equals(value) || ("@id/" + id).equals(value)) return element;
        }
        throw new AssertionError("Missing view: " + id);
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
