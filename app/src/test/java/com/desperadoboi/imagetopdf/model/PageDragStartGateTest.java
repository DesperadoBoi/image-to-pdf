package com.desperadoboi.imagetopdf.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PageDragStartGateTest {
    @Test
    public void handleAndLongPressProduceOneLogicalStart() {
        PageDragStartGate gate = new PageDragStartGate();

        assertTrue(gate.tryStart(42L));
        assertFalse(gate.tryStart(42L));
        assertFalse(gate.tryStart(43L));
        assertEquals(42L, gate.getActivePageId());
    }

    @Test
    public void clearAllowsTheNextDrag() {
        PageDragStartGate gate = new PageDragStartGate();
        gate.tryStart(42L);

        gate.finish(42L);

        assertEquals(PageDragStartGate.NO_ACTIVE_PAGE_ID, gate.getActivePageId());
        assertTrue(gate.tryStart(43L));
    }

    @Test
    public void unrelatedClearDoesNotEndActiveDrag() {
        PageDragStartGate gate = new PageDragStartGate();
        gate.tryStart(42L);

        gate.finish(43L);

        assertEquals(42L, gate.getActivePageId());
        assertFalse(gate.tryStart(43L));
    }
}
