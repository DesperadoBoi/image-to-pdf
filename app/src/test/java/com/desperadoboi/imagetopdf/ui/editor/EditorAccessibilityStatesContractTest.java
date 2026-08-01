package com.desperadoboi.imagetopdf.ui.editor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.xml.parsers.DocumentBuilderFactory;

public final class EditorAccessibilityStatesContractTest {
    private static final String ANDROID = "http://schemas.android.com/apk/res/android";

    @Test public void pageItemRemainsOneAccessibilityNode() throws Exception {
        assertThumbnailExcluded(parse("app/src/main/res/layout/item_page_edit_strip.xml"));
        assertThumbnailExcluded(parse("app/src/main/res/layout-land/item_page_edit_strip.xml"));
        assertCompactAddNode(parse("app/src/main/res/layout/item_editor_add_page.xml"));
    }

    @Test public void reorderUsesOrientationLabelsStableIdsAndExistingMovePath()
            throws Exception {
        String adapter = read("app/src/main/java/com/desperadoboi/imagetopdf/ui/editor/"
                + "EditorPageStripAdapter.java");
        String fragment = read("app/src/main/java/com/desperadoboi/imagetopdf/ui/editor/"
                + "EditorFragment.java");
        String viewModel = read("app/src/main/java/com/desperadoboi/imagetopdf/model/"
                + "DocumentSessionViewModel.java");

        assertTrue(adapter.contains("setHasStableIds(true)"));
        assertTrue(adapter.contains("action_move_page_left"));
        assertTrue(adapter.contains("action_move_page_right"));
        assertTrue(adapter.contains("action_move_page_up"));
        assertTrue(adapter.contains("action_move_page_down"));
        assertTrue(adapter.contains("findViewHolderForItemId(pageId)"));
        assertTrue(adapter.contains("ACTION_ACCESSIBILITY_FOCUS"));
        assertTrue(adapter.contains("page_position_announcement"));
        assertTrue(fragment.contains("return movePage(fromPosition, toPosition)"));
        assertTrue(viewModel.contains("PageOrderManager.move(pages, fromPosition, toPosition)"));
        assertFalse(fragment.contains("selectedPageId = pageId;\n        return movePage"));
    }

    @Test public void responsiveHeaderAndPrimaryActionContractsHold() throws Exception {
        assertResponsiveEditorLayout(parse("app/src/main/res/layout/fragment_editor.xml"));
        assertResponsiveEditorLayout(parse("app/src/main/res/layout-land/fragment_editor.xml"));

        Element compact = parse("app/src/main/res/values/editor_responsive.xml")
                .getDocumentElement();
        Element regular = parse("app/src/main/res/values-w360dp/editor_responsive.xml")
                .getDocumentElement();
        assertTrue(compact.getTextContent().contains("false"));
        assertTrue(regular.getTextContent().contains("true"));
    }

    @Test public void emptyStateUsesExistingAddFlowAndHidesPageOnlyUi() throws Exception {
        Document empty = parse("app/src/main/res/layout/view_editor_empty_state.xml");
        Element action = findById(empty, "button_editor_empty_add");
        assertEquals("@dimen/touch_target", action.getAttributeNS(ANDROID, "minHeight"));
        assertEquals("@string/action_add_images", action.getAttributeNS(ANDROID, "text"));

        String fragment = read("app/src/main/java/com/desperadoboi/imagetopdf/ui/editor/"
                + "EditorFragment.java");
        assertTrue(fragment.contains("emptyStateAddButton.setOnClickListener(v -> openImagePicker())"));
        assertTrue(fragment.contains("pagesRecyclerView.setVisibility(hasPages ? View.VISIBLE : View.GONE)"));
        assertTrue(fragment.contains("createPdfButton.setVisibility(hasPages ? View.VISIBLE : View.GONE)"));
        assertTrue(fragment.contains("rotateSelectedPageButton.setVisibility(hasPages ? View.VISIBLE : View.GONE)"));
        assertTrue(fragment.contains("deleteSelectedPageButton.setVisibility(hasPages ? View.VISIBLE : View.GONE)"));
    }

    @Test public void errorOverlayIsSharedAndRetryUsesExistingLoaders() throws Exception {
        Document overlay = parse("app/src/main/res/layout/view_preview_error.xml");
        assertNotNull(findById(overlay, "text_preview_error"));
        Element retry = findById(overlay, "button_preview_retry");
        assertEquals("@dimen/touch_target", retry.getAttributeNS(ANDROID, "minHeight"));
        assertEquals("@string/action_retry_page_preview", retry.getAttributeNS(ANDROID, "text"));

        String editor = read("app/src/main/java/com/desperadoboi/imagetopdf/ui/editor/"
                + "EditorFragment.java");
        String pageEdit = read("app/src/main/java/com/desperadoboi/imagetopdf/ui/editor/"
                + "PageEditFragment.java");
        assertTrue(editor.contains("previewRetryButton.setOnClickListener"));
        assertTrue(editor.contains("previewImageLoader.load(page"));
        assertTrue(pageEdit.contains("previewRetryButton.setOnClickListener"));
        assertTrue(pageEdit.contains("previewImageLoader.load("));
        assertTrue(editor.contains("previewLoadState.failed(loadedKey)"));
        assertTrue(pageEdit.contains("previewLoadState.failed(key)"));
    }

    @Test public void pendingPreviewRenderIsDiscardedWithDestroyedView() throws Exception {
        String editor = read("app/src/main/java/com/desperadoboi/imagetopdf/ui/editor/"
                + "EditorFragment.java");

        assertTrue(editor.contains("ImageView pendingImageView = selectedPageImageView"));
        assertTrue(editor.contains("isAdded() && selectedPageImageView == pendingImageView"));
        assertTrue(editor.contains("MaterialButton pendingAddButton = emptyStateAddButton"));
        assertTrue(editor.contains("emptyStateAddButton == pendingAddButton"));
        assertTrue(editor.contains("selectedPageImageView = null"));
    }

    @Test public void requiredRussianAndEnglishStringsArePresent() throws Exception {
        Document ru = parse("app/src/main/res/values/strings.xml");
        Document en = parse("app/src/main/res/values-en/editor_strings.xml");

        assertString(ru, "action_move_page_left", "Переместить влево");
        assertString(ru, "action_move_page_right", "Переместить вправо");
        assertString(ru, "action_move_page_up", "Переместить выше");
        assertString(ru, "action_move_page_down", "Переместить ниже");
        assertString(ru, "editor_empty_title", "Добавьте изображения");
        assertString(ru, "editor_empty_description", "Выберите изображения, чтобы создать PDF");
        assertString(ru, "action_add_images", "Добавить изображения");
        assertString(ru, "status_page_preview_load_error", "Не удалось открыть страницу");
        assertString(ru, "action_retry_page_preview", "Повторить");

        assertString(en, "action_move_page_left", "Move left");
        assertString(en, "action_move_page_right", "Move right");
        assertString(en, "action_move_page_up", "Move up");
        assertString(en, "action_move_page_down", "Move down");
        assertString(en, "editor_empty_title", "Add images");
        assertString(en, "editor_empty_description", "Choose images to create a PDF");
        assertString(en, "action_add_images", "Add images");
        assertString(en, "status_page_preview_load_error", "Couldn’t open the page");
        assertString(en, "action_retry_page_preview", "Retry");
    }

    private static void assertThumbnailExcluded(Document document) {
        Element root = document.getDocumentElement();
        Element thumbnail = findById(document, "image_page_edit_strip_thumbnail");
        assertEquals("true", root.getAttributeNS(ANDROID, "focusable"));
        assertEquals("no", thumbnail.getAttributeNS(ANDROID, "importantForAccessibility"));
    }

    private static void assertCompactAddNode(Document document) {
        Element root = document.getDocumentElement();
        assertEquals("true", root.getAttributeNS(ANDROID, "focusable"));
        assertEquals("@string/action_add_page_content_description",
                root.getAttributeNS(ANDROID, "contentDescription"));
        Element label = findById(document, "text_editor_add_page_label");
        assertEquals("no", label.getAttributeNS(ANDROID, "importantForAccessibility"));
    }

    private static void assertResponsiveEditorLayout(Document document) {
        Element title = findById(document, "text_editor_title");
        Element add = findById(document, "button_add_images");
        Element create = findById(document, "button_create_pdf");
        assertEquals("wrap_content", title.getAttributeNS(ANDROID, "layout_height"));
        assertEquals("@dimen/touch_target", title.getAttributeNS(ANDROID, "minHeight"));
        assertEquals("true", title.getAttributeNS(ANDROID, "singleLine"));
        assertEquals("end", title.getAttributeNS(ANDROID, "ellipsize"));
        assertEquals("@dimen/touch_target", add.getAttributeNS(ANDROID, "minWidth"));
        assertEquals("@dimen/touch_target", add.getAttributeNS(ANDROID, "minHeight"));
        assertEquals("@string/action_add_page_content_description",
                add.getAttributeNS(ANDROID, "contentDescription"));
        assertEquals("wrap_content", create.getAttributeNS(ANDROID, "layout_height"));
        assertEquals("@dimen/primary_button_height",
                create.getAttributeNS(ANDROID, "minHeight"));
        assertEquals("2", create.getAttributeNS(ANDROID, "maxLines"));
        assertNotNull(findById(document, "barrier_editor_header_bottom"));
    }

    private static void assertString(Document document, String name, String value) {
        NodeList strings = document.getElementsByTagName("string");
        for (int index = 0; index < strings.getLength(); index++) {
            Element element = (Element) strings.item(index);
            if (name.equals(element.getAttribute("name"))) {
                assertEquals(value, element.getTextContent());
                return;
            }
        }
        throw new AssertionError("Missing string: " + name);
    }

    private static Element findById(Document document, String id) {
        NodeList elements = document.getElementsByTagName("*");
        for (int index = 0; index < elements.getLength(); index++) {
            Element element = (Element) elements.item(index);
            String value = element.getAttributeNS(ANDROID, "id");
            if (("@+id/" + id).equals(value) || ("@id/" + id).equals(value)) {
                return element;
            }
        }
        throw new AssertionError("Missing view: " + id);
    }

    private static String read(String relativePath) throws Exception {
        return Files.readString(repositoryRoot().resolve(relativePath));
    }

    private static Document parse(String relativePath) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(repositoryRoot().resolve(relativePath).toFile());
    }

    private static Path repositoryRoot() {
        Path current = Paths.get("").toAbsolutePath();
        while (current != null && !Files.exists(current.resolve("settings.gradle.kts"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("Repository root not found");
        }
        return current;
    }
}
