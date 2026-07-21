package com.desperadoboi.imagetopdf.model;

import android.net.FakeUri;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PdfSuccessEventTest {
    @Test
    public void eventIsInitiallyNotConsumed() {
        PdfSuccessEvent event = event();

        assertFalse(event.isConsumed());
    }

    @Test
    public void eventCanBeConsumedOnlyOnce() {
        PdfSuccessEvent event = event();

        assertTrue(event.consume());
        assertTrue(event.isConsumed());
        assertFalse(event.consume());
    }

    private PdfSuccessEvent event() {
        return new PdfSuccessEvent(
                1L,
                new PdfResult(
                        FakeUri.create("content://test/result.pdf"),
                        "result.pdf",
                        1024L,
                        1
                )
        );
    }
}
