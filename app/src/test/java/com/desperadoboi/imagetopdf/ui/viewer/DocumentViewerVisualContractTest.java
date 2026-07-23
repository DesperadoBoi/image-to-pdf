package com.desperadoboi.imagetopdf.ui.viewer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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
                "@color/viewer_preview_background",
                byId(document, "@+id/content_viewer_word").getAttributeNS(ANDROID, "background")
        );
        assertEquals(
                "@color/viewer_document_surface",
                byId(document, "@+id/content_viewer_spreadsheet")
                        .getAttributeNS(ANDROID, "background")
        );
    }

    @Test
    public void docxUsesVirtualizedNativeBlocksAndWhiteDocumentSurfaces()
            throws Exception {
        Path root = repositoryRoot();
        Document viewer = parse(root.resolve(
                "app/src/main/res/layout/activity_document_viewer.xml"
        ));
        Element word = byId(viewer, "@+id/content_viewer_word");
        assertEquals(
                "androidx.recyclerview.widget.RecyclerView",
                word.getTagName()
        );
        assertEquals(0, viewer.getElementsByTagName("WebView").getLength());

        Document paragraph = parse(root.resolve(
                "app/src/main/res/layout/item_word_paragraph.xml"
        ));
        assertEquals(
                "true",
                paragraph.getDocumentElement()
                        .getAttributeNS(ANDROID, "textIsSelectable")
        );
        assertEquals(
                "@color/viewer_document_surface",
                paragraph.getDocumentElement().getAttributeNS(ANDROID, "background")
        );

        Document table = parse(root.resolve(
                "app/src/main/res/layout/item_word_table.xml"
        ));
        assertNotNull(byId(table, "@+id/word_table_view"));
        assertEquals(0, table.getElementsByTagName("TableLayout").getLength());
        assertEquals(0, table.getElementsByTagName("TextView").getLength());
    }

    @Test
    public void paragraphRendererMapsRunsToSpansWithoutOneViewPerRun()
            throws Exception {
        Path root = repositoryRoot();
        String factory = Files.readString(root.resolve(
                "app/src/main/java/com/desperadoboi/imagetopdf/ui/viewer/"
                        + "WordSpannableFactory.java"
        ));
        String adapter = Files.readString(root.resolve(
                "app/src/main/java/com/desperadoboi/imagetopdf/ui/viewer/"
                        + "WordBlockAdapter.java"
        ));

        assertTrue(factory.contains("new StyleSpan("));
        assertTrue(factory.contains("new UnderlineSpan()"));
        assertTrue(factory.contains("new StrikethroughSpan()"));
        assertTrue(factory.contains("new ForegroundColorSpan("));
        assertTrue(factory.contains("new BackgroundColorSpan("));
        assertTrue(factory.contains("new AbsoluteSizeSpan("));
        assertTrue(factory.contains("new SubscriptSpan()"));
        assertTrue(factory.contains("new SuperscriptSpan()"));
        assertTrue(adapter.contains("RecyclerView.Adapter<RecyclerView.ViewHolder>"));
        assertFalse(adapter.contains("for (WordRun"));
    }

    @Test
    public void docxParsingSurvivesRotationAndImagesStayLazyAndBounded()
            throws Exception {
        Path root = repositoryRoot();
        String activity = Files.readString(root.resolve(
                "app/src/main/java/com/desperadoboi/imagetopdf/ui/viewer/"
                        + "DocumentViewerActivity.java"
        ));
        String viewModel = Files.readString(root.resolve(
                "app/src/main/java/com/desperadoboi/imagetopdf/ui/viewer/"
                        + "WordViewerViewModel.java"
        ));
        String images = Files.readString(root.resolve(
                "app/src/main/java/com/desperadoboi/imagetopdf/document/word/"
                        + "WordImageLoader.java"
        ));

        assertTrue(activity.contains("new ViewModelProvider(this)"));
        assertTrue(viewModel.contains("extends ViewModel"));
        assertTrue(viewModel.contains("Executors.newSingleThreadExecutor()"));
        assertTrue(viewModel.contains("new DocxDocumentParser().parse("));
        assertTrue(images.contains("MAX_CACHE_BYTES"));
        assertTrue(images.contains("inSampleSize"));
        assertTrue(images.contains("decodeExecutor"));
        assertTrue(images.contains("MAX_PIXELS"));
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
    public void spreadsheetUsesUnifiedTwoDimensionalViewportAndTransientZoomIndicator()
            throws Exception {
        Document document = parse(repositoryRoot().resolve(
                "app/src/main/res/layout/activity_document_viewer.xml"
        ));
        Element viewport = byId(document, "@+id/viewport_viewer_spreadsheet");
        Element indicator = byId(document, "@+id/text_viewer_zoom_indicator");

        assertEquals("com.desperadoboi.imagetopdf.ui.viewer.SpreadsheetCanvasView",
                viewport.getTagName());
        assertEquals("false", indicator.getAttributeNS(ANDROID, "clickable"));
        assertEquals("no", indicator.getAttributeNS(ANDROID, "importantForAccessibility"));
        assertEquals(0, document.getElementsByTagName("HorizontalScrollView").getLength());
    }

    @Test
    public void xlsxCsvAndTsvShareCanvasModelPipelineWithoutLegacyRenderer() throws Exception {
        Path root = repositoryRoot();
        String activity = Files.readString(root.resolve(
                "app/src/main/java/com/desperadoboi/imagetopdf/ui/viewer/"
                        + "DocumentViewerActivity.java"
        ));

        assertTrue(activity.contains("SpreadsheetCanvasModel.create(0, data, density)"));
        assertTrue(activity.contains("SpreadsheetCanvasModel.create("));
        assertTrue(activity.contains("workbook.getSheets().get(sheetIndex)"));
        assertFalse(Files.exists(root.resolve(
                "app/src/main/java/com/desperadoboi/imagetopdf/ui/viewer/"
                        + "SpreadsheetRowAdapter.java"
        )));
        assertFalse(Files.exists(root.resolve(
                "app/src/main/java/com/desperadoboi/imagetopdf/ui/viewer/"
                        + "SpreadsheetCellView.java"
        )));
    }

    @Test
    public void toolbarKeepsOnlyNavigationTitleShareAndOverflow() throws Exception {
        Document document = parse(repositoryRoot().resolve(
                "app/src/main/res/layout/activity_document_viewer.xml"
        ));

        assertNotNull(findById(document, "@+id/button_viewer_back"));
        assertNotNull(findById(document, "@+id/text_viewer_title"));
        assertNotNull(findById(document, "@+id/button_viewer_share"));
        assertNotNull(findById(document, "@+id/button_viewer_more"));
        assertNull(findById(document, "@+id/button_viewer_fit_width"));
    }

    @Test
    public void overflowContainsOnlyLocalizedOneHundredPercentSpreadsheetCommand()
            throws Exception {
        Document menu = parse(repositoryRoot().resolve(
                "app/src/main/res/menu/document_viewer_overflow.xml"
        ));
        assertEquals(
                "@string/viewer_reset_zoom_100",
                byId(menu, "@+id/action_viewer_zoom_100").getAttributeNS(ANDROID, "title")
        );
        assertNull(findById(menu, "@+id/action_viewer_fit_width"));
        assertNull(findById(menu, "@+id/action_viewer_fit_sheet"));

        Document russian = parse(repositoryRoot().resolve(
                "app/src/main/res/values/strings.xml"
        ));
        Document english = parse(repositoryRoot().resolve(
                "app/src/main/res/values-en/viewer_strings.xml"
        ));
        assertEquals(
                "Вернуть масштаб 100%",
                namedText(russian, "viewer_reset_zoom_100")
        );
        assertEquals(
                "Reset zoom to 100%",
                namedText(english, "viewer_reset_zoom_100")
        );
        assertNull(findNamed(russian, "string", "viewer_zoom_100"));
        assertNull(findNamed(english, "string", "viewer_zoom_100"));
        assertFalse(Files.readString(repositoryRoot().resolve(
                "app/src/main/res/values/strings.xml"
        )).contains("Масштаб " + "100%"));
        String[] removedFitStrings = {
                "viewer_fit_width",
                "viewer_fit_width_content_description",
                "viewer_fit_width_applied",
                "viewer_fit_sheet",
                "viewer_fit_sheet_applied"
        };
        for (String removedFitString : removedFitStrings) {
            assertNull(findNamed(russian, "string", removedFitString));
            assertNull(findNamed(english, "string", removedFitString));
        }
    }

    @Test
    public void overflowZoomCommandIsSpreadsheetOnlyAndReturnsToOneHundredPercent()
            throws Exception {
        String activity = Files.readString(repositoryRoot().resolve(
                "app/src/main/java/com/desperadoboi/imagetopdf/ui/viewer/"
                        + "DocumentViewerActivity.java"
        ));

        assertTrue(activity.contains("findItem(R.id.action_viewer_zoom_100)"));
        assertTrue(activity.contains("ZoomController.shouldShowResetAction("));
        assertTrue(activity.contains(".setVisible(resetVisible);"));
        assertTrue(activity.contains("spreadsheetCanvasView.resetTo100Percent();"));
    }

    @Test
    public void spreadsheetDoubleTapAlwaysReturnsToOneHundredPercent() throws Exception {
        String canvas = Files.readString(repositoryRoot().resolve(
                "app/src/main/java/com/desperadoboi/imagetopdf/ui/viewer/"
                        + "SpreadsheetCanvasView.java"
        ));

        assertTrue(canvas.contains("public boolean onDoubleTap(MotionEvent event)"));
        assertTrue(canvas.contains("resetTo100Percent("));
        assertFalse(canvas.contains("fitToWidth()"));
        assertFalse(canvas.contains("fitToSheet()"));
    }

    @Test
    public void spreadsheetResetIsOneAtomicPathForOverflowAndDoubleTap() throws Exception {
        String canvas = Files.readString(repositoryRoot().resolve(
                "app/src/main/java/com/desperadoboi/imagetopdf/ui/viewer/"
                        + "SpreadsheetCanvasView.java"
        ));
        int resetImplementation = canvas.indexOf(
                "private void resetTo100Percent(float focalContentX, float focalContentY)"
        );
        int resetEnd = canvas.indexOf("private void drawFills(", resetImplementation);
        String resetBody = canvas.substring(resetImplementation, resetEnd);

        assertTrue(resetBody.contains("stopMotion();"));
        assertTrue(resetBody.contains("pendingState = null;"));
        assertTrue(resetBody.contains("transform.zoomAround("));
        assertTrue(resetBody.contains("ZoomController.NORMAL_ZOOM"));
        assertTrue(resetBody.contains("zoomMode = ZoomController.ZoomMode.ZOOM_100;"));
        assertTrue(resetBody.contains("viewportChanged();"));
        assertTrue(resetBody.contains("notifyZoomChanged(true, true);"));
        assertFalse(canvas.contains("zoomToNormal"));
    }

    @Test
    public void spreadsheetAccessibilityHasNoFitActions() throws Exception {
        Path root = repositoryRoot();
        String helper = Files.readString(root.resolve(
                "app/src/main/java/com/desperadoboi/imagetopdf/ui/viewer/"
                        + "SpreadsheetCanvasAccessibilityHelper.java"
        ));
        Document ids = parse(root.resolve(
                "app/src/main/res/values/spreadsheet_accessibility_ids.xml"
        ));

        assertNotNull(findNamed(
                ids,
                "item",
                "accessibility_action_spreadsheet_zoom_100"
        ));
        assertNull(findNamed(ids, "item", "accessibility_action_spreadsheet_fit_width"));
        assertNull(findNamed(ids, "item", "accessibility_action_spreadsheet_fit_sheet"));
        assertTrue(helper.contains("ACTION_SCROLL_LEFT"));
        assertTrue(helper.contains("ACTION_SCROLL_RIGHT"));
        assertTrue(helper.contains("ACTION_SCROLL_UP"));
        assertTrue(helper.contains("ACTION_SCROLL_DOWN"));
        assertFalse(helper.contains("ACTION_FIT_WIDTH"));
        assertFalse(helper.contains("ACTION_FIT_SHEET"));
    }

    @Test
    public void spreadsheetHasNoPermanentFitIcon() {
        assertFalse(Files.exists(repositoryRoot().resolve(
                "app/src/main/res/drawable/ic_action_fit_width_24.xml"
        )));
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
        Element element = findById(document, id);
        if (element != null) return element;
        throw new AssertionError("Missing view " + id);
    }

    private static Element findById(Document document, String id) {
        NodeList elements = document.getElementsByTagName("*");
        for (int index = 0; index < elements.getLength(); index++) {
            Element element = (Element) elements.item(index);
            if (id.equals(element.getAttributeNS(ANDROID, "id"))) return element;
        }
        return null;
    }

    private static Element findNamed(Document document, String tag, String name) {
        NodeList elements = document.getElementsByTagName(tag);
        for (int index = 0; index < elements.getLength(); index++) {
            Element element = (Element) elements.item(index);
            if (name.equals(element.getAttribute("name"))) return element;
        }
        return null;
    }

    private static String namedText(Document document, String name) {
        Element element = findNamed(document, "string", name);
        if (element == null) element = findNamed(document, "dimen", name);
        if (element == null) throw new AssertionError("Missing resource " + name);
        return element.getTextContent();
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
