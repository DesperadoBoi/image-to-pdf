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
        store.save(0, positioned(0.5f, 10f, 20f, true));
        store.save(1, positioned(1.5f, 30f, 40f, false));

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
        store.save(2, positioned(1.25f, 75f, 180f, false));

        SpreadsheetStateStore restored = store.copy();

        assertEquals(2, restored.getSelectedSheet());
        assertEquals(1.25f, restored.restore(2).getScale(), DELTA);
        assertEquals(75f, restored.restore(2).getHorizontalOffset(), DELTA);
        assertEquals(180f, restored.restore(2).getVerticalOffset(), DELTA);
        assertEquals(220f, restored.restore(2).getCenterContentY(), DELTA);
    }

    @Test
    public void openingAnotherDocumentClearsPreviousStates() {
        SpreadsheetStateStore store = new SpreadsheetStateStore();
        store.openDocument("document-a");
        store.save(0, positioned(1.5f, 30f, 40f, false));

        store.openDocument("document-b");

        assertEquals(0, store.size());
        assertTrue(store.restore(0).isFitWidth());
        assertFalse(store.restore(0).hasViewportPosition());
    }

    @Test
    public void manualZoomDisablesFitWidthMode() {
        SpreadsheetStateStore store = new SpreadsheetStateStore();
        store.openDocument("document-a");
        assertTrue(store.restore(0).isFitWidth());

        store.recordManualZoom(0, 1.4f);

        assertFalse(store.restore(0).isFitWidth());
        assertEquals(1.4f, store.restore(0).getScale(), DELTA);
    }

    private SpreadsheetViewportState positioned(
            float scale,
            float horizontal,
            float vertical,
            boolean fitWidth
    ) {
        return SpreadsheetViewportState.positioned(
                scale,
                horizontal,
                vertical,
                120f,
                220f,
                fitWidth
        );
    }
}
