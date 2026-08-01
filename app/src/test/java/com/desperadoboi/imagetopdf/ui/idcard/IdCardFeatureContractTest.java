package com.desperadoboi.imagetopdf.ui.idcard;

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
import java.util.stream.Stream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class IdCardFeatureContractTest {
    private static final String ANDROID = "http://schemas.android.com/apk/res/android";

    @Test public void catalogItemIsActiveAndHasNoComingSoonBadge() throws Exception {
        String catalog = read("app/src/main/java/com/desperadoboi/imagetopdf/ui/tools/ToolCatalog.java");
        String adapter = read("app/src/main/java/com/desperadoboi/imagetopdf/ui/tools/AllToolsAdapter.java");

        assertTrue(catalog.contains("tool(ToolId.ID_SCAN"));
        assertTrue(catalog.contains("R.drawable.ic_tool_id_scan_safe"));
        assertTrue(catalog.contains("ToolAvailability.AVAILABLE, false"));
        assertTrue(adapter.contains("R.string.tool_id_scan_description"));
    }

    @Test public void localizedToolNamesAndDescriptionsAreComplete() throws Exception {
        assertString(parse("app/src/main/res/values/strings.xml"),
                "tool_id_scan", "Скан удостоверения");
        assertString(parse("app/src/main/res/values-en/all_tools_strings.xml"),
                "tool_id_scan", "ID card scan");
        assertString(parse("app/src/main/res/values/id_card_strings.xml"),
                "tool_id_scan_description",
                "Сканирование лицевой и обратной стороны удостоверения");
        assertString(parse("app/src/main/res/values-en/id_card_strings.xml"),
                "tool_id_scan_description", "Scan the front and back of an ID card");
    }

    @Test public void defaultFilenamesContainOnlyLocalizedPrefixAndDate() throws Exception {
        assertString(parse("app/src/main/res/values/id_card_strings.xml"),
                "id_card_file_name_template", "Скан_удостоверения_%1$s.pdf");
        assertString(parse("app/src/main/res/values-en/id_card_strings.xml"),
                "id_card_file_name_template", "ID_scan_%1$s.pdf");
    }

    @Test public void idGalleryUsesImageOnlyPhotoPickerAndNoMediaStore() throws Exception {
        String review = read("app/src/main/java/com/desperadoboi/imagetopdf/ui/idcard/IdCardScanFragment.java");
        String camera = read("app/src/main/java/com/desperadoboi/imagetopdf/ui/smartscan/SmartScanFragment.java");

        assertTrue(review.contains("ActivityResultContracts.PickVisualMedia"));
        assertTrue(review.contains("PickVisualMedia.ImageOnly.INSTANCE"));
        assertTrue(camera.contains("PickVisualMedia.ImageOnly.INSTANCE"));
        assertFalse(review.contains("MediaStore"));
        assertFalse(review.contains("READ_MEDIA_IMAGES"));
        assertFalse(review.contains("READ_EXTERNAL_STORAGE"));
    }

    @Test public void idFlowAddsNoNetworkOrStoragePermissionUsage() throws Exception {
        String manifest = read("app/src/main/AndroidManifest.xml");
        String idPackage = readJavaPackage("app/src/main/java/com/desperadoboi/imagetopdf/ui/idcard");

        assertFalse(manifest.contains("android.permission.INTERNET"));
        assertFalse(manifest.contains("android.permission.MANAGE_EXTERNAL_STORAGE"));
        assertFalse(manifest.contains("android.permission.WRITE_EXTERNAL_STORAGE"));
        assertFalse(idPackage.contains("READ_MEDIA_IMAGES"));
        assertFalse(idPackage.contains("READ_EXTERNAL_STORAGE"));
        assertFalse(idPackage.contains("MANAGE_EXTERNAL_STORAGE"));
        assertFalse(idPackage.contains("http://"));
        assertFalse(idPackage.contains("https://"));
    }

    @Test public void dependenciesContainNoNetworkOcrOrDocumentSdk() throws Exception {
        String gradle = read("app/build.gradle.kts").toLowerCase(Locale.ROOT);

        for (String forbidden : Arrays.asList(
                "retrofit", "okhttp", "firebase", "mlkit", "tesseract", "apache poi"
        )) {
            assertFalse("Unexpected dependency: " + forbidden, gradle.contains(forbidden));
        }
    }

    @Test public void cacheUsesRandomNamesTtlAndNoSensitiveLogging() throws Exception {
        String storage = read("app/src/main/java/com/desperadoboi/imagetopdf/ui/idcard/IdCardCacheStorage.java");
        String idPackage = readJavaPackage("app/src/main/java/com/desperadoboi/imagetopdf/ui/idcard");

        assertTrue(storage.contains("UUID.randomUUID()"));
        assertTrue(storage.contains("DEFAULT_TTL_MS"));
        assertTrue(storage.contains("getCacheDir()"));
        assertFalse(idPackage.contains("android.util.Log"));
        assertFalse(idPackage.contains("Log."));
        assertFalse(idPackage.contains("Clipboard"));
        assertFalse(idPackage.contains("MediaStore"));
    }

    @Test public void stateRestorationStoresUrisRotationQuadAndOptionsButNoBitmap() throws Exception {
        String viewModel = read("app/src/main/java/com/desperadoboi/imagetopdf/ui/idcard/IdCardScanViewModel.java");

        assertTrue(viewModel.contains("IMAGE_URI"));
        assertTrue(viewModel.contains("IMAGE_ROTATION"));
        assertTrue(viewModel.contains("IMAGE_QUAD"));
        assertTrue(viewModel.contains("KEY_PRESET"));
        assertTrue(viewModel.contains("KEY_WATERMARK_ENABLED"));
        assertTrue(viewModel.contains("KEY_WATERMARK_TEXT"));
        assertTrue(viewModel.contains("IdCardError.FILE_UNAVAILABLE"));
        assertFalse(viewModel.contains("android.graphics.Bitmap"));
    }

    @Test public void reviewLayoutsAreScrollableCompactAndResponsive() throws Exception {
        Document portrait = parse("app/src/main/res/layout/fragment_id_card_scan.xml");
        Document landscape = parse("app/src/main/res/layout-land/fragment_id_card_scan.xml");

        assertNotNull(findById(portrait, "scroll_id_card"));
        assertNotNull(findById(landscape, "scroll_id_card"));
        assertNotNull(findById(portrait, "card_id_front"));
        assertNotNull(findById(portrait, "card_id_back"));
        assertNotNull(findById(landscape, "card_id_front"));
        assertNotNull(findById(landscape, "card_id_back"));
        assertEquals("@dimen/id_card_content_max_width",
                findById(portrait, "scroll_id_card").getAttributeNS(
                        "http://schemas.android.com/apk/res-auto",
                        "layout_constraintWidth_max"
                ));
        assertFalse(read("app/src/main/res/layout/view_id_card_side.xml")
                .contains("android:layout_height=\"200dp\""));
    }

    @Test public void sideActionsMeetTouchTargetsAndSlotsAreAccessibilityGroups()
            throws Exception {
        Document side = parse("app/src/main/res/layout/view_id_card_side.xml");
        Element root = side.getDocumentElement();

        assertEquals("true", root.getAttributeNS(ANDROID, "screenReaderFocusable"));
        for (String id : Arrays.asList(
                "button_id_card_camera", "button_id_card_gallery",
                "button_id_card_replace", "button_id_card_retake",
                "button_id_card_rotate", "button_id_card_correct",
                "button_id_card_delete"
        )) {
            assertEquals(
                    id,
                    "@dimen/touch_target",
                    findById(side, id).getAttributeNS(ANDROID, "minHeight")
            );
        }
        assertEquals("no", findById(side, "image_id_card_preview")
                .getAttributeNS(ANDROID, "importantForAccessibility"));
        assertEquals("no", findById(side, "image_id_card_placeholder")
                .getAttributeNS(ANDROID, "importantForAccessibility"));
    }

    @Test public void allFiveSideStatesAndRequiredActionsAreRepresented() throws Exception {
        assertEquals(5, IdCardSideState.values().length);
        String strings = read("app/src/main/res/values-en/id_card_strings.xml");
        for (String key : Arrays.asList(
                "id_card_state_empty", "id_card_state_capturing",
                "id_card_state_processing", "id_card_state_ready", "id_card_state_error",
                "id_card_action_take_photo", "id_card_action_choose_image",
                "id_card_action_replace", "id_card_action_retake", "id_card_action_rotate",
                "id_card_action_correct", "id_card_action_delete", "id_card_action_swap"
        )) {
            assertTrue("Missing resource: " + key, strings.contains("name=\"" + key + "\""));
        }
    }

    @Test public void watermarkInputHasLabelCounterAndHardLimit() throws Exception {
        Document options = parse("app/src/main/res/layout/view_id_card_export_options.xml");
        Element layout = findById(options, "input_layout_id_card_watermark");
        Element input = findById(options, "input_id_card_watermark");

        assertEquals("@string/id_card_watermark_label",
                layout.getAttributeNS(ANDROID, "hint"));
        assertEquals("true", layout.getAttributeNS(
                "http://schemas.android.com/apk/res-auto", "counterEnabled"));
        assertEquals("40", layout.getAttributeNS(
                "http://schemas.android.com/apk/res-auto", "counterMaxLength"));
        assertEquals("40", input.getAttributeNS(ANDROID, "maxLength"));
    }

    @Test public void pdfGeneratorUsesTempOutputCancellationAndResourceCleanup() throws Exception {
        String generator = read("app/src/main/java/com/desperadoboi/imagetopdf/pdf/IdCardPdfGenerator.java");

        assertTrue(generator.contains("File.createTempFile"));
        assertTrue(generator.contains("throwIfCancelled(cancellationToken)"));
        assertTrue(generator.contains("deletePartialOutput(outputUri)"));
        assertTrue(generator.contains("deleteTemporaryFile(temporaryFile)"));
        assertTrue(generator.contains("recycleAll(renderedBitmaps)"));
        assertTrue(generator.contains("try (InputStream input"));
        assertTrue(generator.contains("try (OutputStream output"));
        assertTrue(generator.contains("document.close()"));
        assertFalse(generator.contains("ExifInterface.saveAttributes"));
    }

    @Test public void watermarkIsFlattenedForEveryRenderedSideAndNeverUsedAsFilename()
            throws Exception {
        String generator = read("app/src/main/java/com/desperadoboi/imagetopdf/pdf/IdCardPdfGenerator.java");
        String fragment = read("app/src/main/java/com/desperadoboi/imagetopdf/ui/idcard/IdCardScanFragment.java");

        int loop = generator.indexOf("for (int index = 0; index < sideImages.size(); index++)");
        int watermark = generator.indexOf("drawWatermark(canvas, drawnRect", loop);
        int finish = generator.indexOf("document.finishPage(page)", loop);
        assertTrue(loop >= 0 && watermark > loop && finish > watermark);
        assertTrue(fragment.contains("R.string.id_card_file_name_template"));
        assertFalse(fragment.contains("getWatermarkText() + \".pdf\""));
    }

    @Test public void privacyArtifactsDescribeLocalIdProcessingAndDeletion() throws Exception {
        for (String path : Arrays.asList(
                "app/src/main/res/values-en/strings.xml",
                "docs/PRIVACY_POLICY_EN.md",
                "docs/privacy/en/index.html"
        )) {
            String text = read(path).toLowerCase(Locale.ROOT);
            assertTrue(path, text.contains("id-card"));
            assertTrue(path, text.contains("locally") || text.contains("local"));
            assertTrue(path, text.contains("deleted"));
            assertTrue(path, text.contains("explicit"));
        }
        for (String path : Arrays.asList(
                "app/src/main/res/values/about_privacy_strings.xml",
                "docs/PRIVACY_POLICY_RU.md",
                "docs/privacy/index.html"
        )) {
            String text = read(path).toLowerCase(new Locale("ru"));
            assertTrue(path, text.contains("удостовер"));
            assertTrue(path, text.contains("локаль"));
            assertTrue(path, text.contains("удал"));
            assertTrue(path, text.contains("явн"));
        }
    }

    @Test public void repositoryContainsNoIdDocumentFixturesOrScreenshots() throws Exception {
        Path root = repositoryRoot();
        try (Stream<Path> files = Files.walk(root.resolve("app/src/test"))) {
            files.filter(Files::isRegularFile).forEach(path -> {
                String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                assertFalse("Unexpected ID fixture: " + path,
                        name.matches(".*(passport|identity_card|real_id|iin).*"));
                assertFalse("Unexpected screenshot: " + path,
                        name.matches(".*id.*\\.(png|jpe?g|webp)$"));
            });
        }
    }

    private static String readJavaPackage(String relativeDirectory) throws Exception {
        StringBuilder result = new StringBuilder();
        try (Stream<Path> files = Files.walk(repositoryRoot().resolve(relativeDirectory))) {
            for (Path path : (Iterable<Path>) files.filter(
                    file -> Files.isRegularFile(file) && file.toString().endsWith(".java")
            )::iterator) {
                result.append(Files.readString(path, StandardCharsets.UTF_8)).append('\n');
            }
        }
        return result.toString();
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
