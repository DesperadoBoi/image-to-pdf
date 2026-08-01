package com.desperadoboi.imagetopdf.ui.editor;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class DocumentPerspectiveAccessibilityContractTest {
    @Test public void overlayUsesVirtualChildrenCustomActionsAndKeyboardArrows() throws Exception {
        String overlay = read(
                "app/src/main/java/com/desperadoboi/imagetopdf/ui/editor/"
                        + "DocumentPerspectiveOverlayView.java"
        );

        assertTrue(overlay.contains("extends ExploreByTouchHelper"));
        assertTrue(overlay.contains("getVisibleVirtualViews"));
        assertTrue(overlay.contains("getVirtualViewAt"));
        assertTrue(overlay.contains("setBoundsInParent"));
        assertTrue(overlay.contains("KEYCODE_DPAD_LEFT"));
        assertTrue(overlay.contains("KEYCODE_DPAD_RIGHT"));
        assertTrue(overlay.contains("KEYCODE_DPAD_UP"));
        assertTrue(overlay.contains("KEYCODE_DPAD_DOWN"));
        assertTrue(overlay.contains("TYPE_ANNOUNCEMENT"));
        assertTrue(overlay.contains("invalidateVirtualView(virtualId)"));
        assertTrue(overlay.contains("onTouchEvent(MotionEvent event)"));
        assertTrue(overlay.contains("PerspectiveQuadEditor.moveHandle"));
    }

    @Test public void allCornerLabelsAndActionsAreLocalized() throws Exception {
        String ru = read("app/src/main/res/values/strings.xml");
        String en = read("app/src/main/res/values-en/editor_strings.xml");

        for (String name : Arrays.asList(
                "document_overlay_content_description",
                "document_corner_top_left", "document_corner_top_right",
                "document_corner_bottom_left", "document_corner_bottom_right",
                "document_corner_position_description", "document_corner_move_left",
                "document_corner_move_right", "document_corner_move_up",
                "document_corner_move_down"
        )) {
            assertTrue("Missing RU " + name, ru.contains("name=\"" + name + "\""));
            assertTrue("Missing EN " + name, en.contains("name=\"" + name + "\""));
        }
        assertTrue(ru.contains("Переместить вверх"));
        assertTrue(en.contains("Top-left corner"));
    }

    @Test public void sharedOverlayServesSmartScanAndIdCardWithoutDecorativeNodes()
            throws Exception {
        String review = read(
                "app/src/main/java/com/desperadoboi/imagetopdf/ui/smartscan/"
                        + "ScanReviewFragment.java"
        );
        String layout = read("app/src/main/res/layout/fragment_scan_review.xml");
        String model = read(
                "app/src/main/java/com/desperadoboi/imagetopdf/ui/editor/"
                        + "PerspectiveCornerAccessibilityModel.java"
        );

        assertTrue(review.contains("DocumentPerspectiveOverlayView"));
        assertTrue(review.contains("idCardMode"));
        assertTrue(layout.contains("DocumentPerspectiveOverlayView"));
        assertTrue(model.contains("VIRTUAL_CHILD_COUNT = 4"));
        assertFalse(model.contains("GRID_ID"));
        assertFalse(model.contains("GUIDE_ID"));
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
