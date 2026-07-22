package com.desperadoboi.imagetopdf.ui.viewer;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class SpreadsheetAxisOffsetsTest {
    private static final float DELTA = 0.0001f;

    @Test
    public void headersUseOnlyTheirRelevantAxis() {
        SpreadsheetAxisOffsets offsets = new SpreadsheetAxisOffsets();
        offsets.setHorizontal(145f);
        offsets.setVertical(280f);

        assertEquals(offsets.getBodyHorizontal(), offsets.getColumnHeaderHorizontal(), DELTA);
        assertEquals(offsets.getBodyVertical(), offsets.getRowHeaderVertical(), DELTA);
        assertEquals(0f, offsets.getRowHeaderHorizontal(), DELTA);
        assertEquals(0f, offsets.getColumnHeaderVertical(), DELTA);
    }
}
