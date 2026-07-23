package com.desperadoboi.imagetopdf.ui.viewer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class SpreadsheetStateStoreTest {
    private static final float DELTA = 0.0001f;

    @Test
    public void storesIndependentViewportForEachSheet() {
        SpreadsheetStateStore store = new SpreadsheetStateStore();
        store.openDocument("document-a");
        store.save(0, positioned(
                0.6f,
                10f,
                20f,
                ZoomController.ZoomMode.MANUAL
        ));
        store.save(1, positioned(
                1.5f,
                30f,
                40f,
                ZoomController.ZoomMode.MANUAL
        ));

        assertEquals(0.6f, store.restore(0).getScale(), DELTA);
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
    public void newXlsxSheetStartsAtOneHundredPercentAndRestoresSavedState() {
        SpreadsheetStateStore store = new SpreadsheetStateStore();
        store.openDocument("document-a");

        SpreadsheetViewportState initial = store.restoreXlsx(3);
        assertEquals(ZoomController.ZoomMode.ZOOM_100, initial.getZoomMode());
        assertEquals(1f, initial.getScale(), DELTA);
        assertEquals(0f, initial.getHorizontalOffset(), DELTA);
        assertEquals(0f, initial.getVerticalOffset(), DELTA);
        assertFalse(initial.hasViewportPosition());

        store.save(3, positioned(
                1.4f,
                35f,
                80f,
                ZoomController.ZoomMode.MANUAL
        ));
        assertEquals(1.4f, store.restoreXlsx(3).getScale(), DELTA);
        assertEquals(ZoomController.ZoomMode.MANUAL, store.restoreXlsx(3).getZoomMode());
    }

    @Test
    public void recordedManualZoomUsesManualClampAndMode() {
        SpreadsheetStateStore store = new SpreadsheetStateStore();
        store.openDocument("document-a");

        store.recordManualZoom(0, 0.25f);

        assertEquals(ZoomController.ZoomMode.MANUAL, store.restore(0).getZoomMode());
        assertEquals(0.60f, store.restore(0).getScale(), DELTA);
    }

    @Test
    public void resetStateIsStoredForCurrentSheetWithoutChangingSelection() {
        SpreadsheetStateStore store = new SpreadsheetStateStore();
        store.openDocument("document-a");
        store.setSelectedSheet(3);
        store.save(3, positioned(
                0.60f,
                300f,
                200f,
                ZoomController.ZoomMode.MANUAL
        ));

        store.save(3, SpreadsheetViewportState.positioned(
                1f,
                150f,
                100f,
                300f,
                200f,
                ZoomController.ZoomMode.ZOOM_100
        ));

        assertEquals(3, store.getSelectedSheet());
        assertEquals(1f, store.restore(3).getScale(), DELTA);
        assertEquals(ZoomController.ZoomMode.ZOOM_100, store.restore(3).getZoomMode());
        assertFalse(ZoomController.shouldShowResetAction(store.restore(3).getScale()));
    }

    @Test
    public void sheetSwitchAndRotationUseEachRestoredScaleForMenuState() {
        SpreadsheetStateStore store = new SpreadsheetStateStore();
        store.openDocument("document-a");
        store.save(0, positioned(
                0.60f,
                10f,
                20f,
                ZoomController.ZoomMode.MANUAL
        ));
        store.save(1, positioned(
                1f,
                30f,
                40f,
                ZoomController.ZoomMode.ZOOM_100
        ));
        store.setSelectedSheet(1);

        assertFalse(ZoomController.shouldShowResetAction(store.restore(1).getScale()));
        assertTrue(ZoomController.shouldShowResetAction(store.restore(0).getScale()));

        SpreadsheetStateStore rotated = store.copy();
        assertEquals(1, rotated.getSelectedSheet());
        assertFalse(ZoomController.shouldShowResetAction(
                rotated.restore(rotated.getSelectedSheet()).getScale()
        ));
    }

    @Test
    public void nearOneHundredPercentRestoresAsZoomOneHundredMode() {
        SpreadsheetViewportState state = positioned(
                0.9995f,
                10f,
                20f,
                ZoomController.ZoomMode.MANUAL
        );

        assertEquals(1f, state.getScale(), DELTA);
        assertEquals(ZoomController.ZoomMode.ZOOM_100, state.getZoomMode());
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
