package com.desperadoboi.imagetopdf.ui.viewer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

public final class SpreadsheetStateStoreTest {
    private static final float DELTA = 0.0001f;

    @Test
    public void storesIndependentViewportForEachSheet() {
        SpreadsheetStateStore store = new SpreadsheetStateStore();
        store.openDocument("document-a");
        store.save(0, positioned(
                0.5f,
                10f,
                20f,
                ZoomController.ZoomMode.FIT_WIDTH
        ));
        store.save(1, positioned(
                1.5f,
                30f,
                40f,
                ZoomController.ZoomMode.MANUAL
        ));

        assertEquals(0.5f, store.restore(0).getScale(), DELTA);
        assertEquals(10f, store.restore(0).getHorizontalOffset(), DELTA);
        assertEquals(1.5f, store.restore(1).getScale(), DELTA);
        assertEquals(40f, store.restore(1).getVerticalOffset(), DELTA);
    }

    @Test
    public void copiedStoreRestoresRotationStateAndSelectedSheet() {
        SpreadsheetStateStore store = new SpreadsheetStateStore();
        store.openDocument("document-a");
        store.setSelectedSheet(2);
        store.save(2, positioned(
                1.25f,
                75f,
                180f,
                ZoomController.ZoomMode.MANUAL
        ));

        SpreadsheetStateStore restored = store.copy();

        assertEquals(2, restored.getSelectedSheet());
        assertEquals(1.25f, restored.restore(2).getScale(), DELTA);
        assertEquals(75f, restored.restore(2).getHorizontalOffset(), DELTA);
        assertEquals(180f, restored.restore(2).getVerticalOffset(), DELTA);
        assertEquals(220f, restored.restore(2).getCenterContentY(), DELTA);
        assertEquals(ZoomController.ZoomMode.MANUAL, restored.restore(2).getZoomMode());
    }

    @Test
    public void openingAnotherDocumentClearsPreviousStates() {
        SpreadsheetStateStore store = new SpreadsheetStateStore();
        store.openDocument("document-a");
        store.save(0, positioned(
                1.5f,
                30f,
                40f,
                ZoomController.ZoomMode.MANUAL
        ));

        store.openDocument("document-b");

        assertEquals(0, store.size());
        assertEquals(1f, store.restore(0).getScale(), DELTA);
        assertEquals(ZoomController.ZoomMode.ZOOM_100, store.restore(0).getZoomMode());
        assertFalse(store.restore(0).hasViewportPosition());
    }

    @Test
    public void newSheetStartsAtNormalZoomAndA1() {
        SpreadsheetStateStore store = new SpreadsheetStateStore();
        store.openDocument("document-a");

        SpreadsheetViewportState state = store.restore(4);

        assertEquals(1f, state.getScale(), DELTA);
        assertEquals(ZoomController.ZoomMode.ZOOM_100, state.getZoomMode());
        assertEquals(0f, state.getHorizontalOffset(), DELTA);
        assertEquals(0f, state.getVerticalOffset(), DELTA);
        assertFalse(state.hasViewportPosition());
    }

    @Test
    public void pinchAfterFitWidthUsesManualClampAndMode() {
        SpreadsheetStateStore store = new SpreadsheetStateStore();
        store.openDocument("document-a");
        store.save(0, positioned(
                0.25f,
                0f,
                0f,
                ZoomController.ZoomMode.FIT_WIDTH
        ));

        store.recordManualZoom(0, 0.25f);

        assertEquals(ZoomController.ZoomMode.MANUAL, store.restore(0).getZoomMode());
        assertEquals(0.60f, store.restore(0).getScale(), DELTA);
    }

    private SpreadsheetViewportState positioned(
            float scale,
            float horizontal,
            float vertical,
            ZoomController.ZoomMode zoomMode
    ) {
        return SpreadsheetViewportState.positioned(
                scale,
                horizontal,
                vertical,
                120f,
                220f,
                zoomMode
        );
    }
}
