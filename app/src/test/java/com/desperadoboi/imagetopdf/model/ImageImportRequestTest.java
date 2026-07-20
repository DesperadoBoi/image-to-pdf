package com.desperadoboi.imagetopdf.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class ImageImportRequestTest {
    @Test
    public void sourceIsRequired() {
        assertThrows(
                NullPointerException.class,
                () -> new ImageImportRequest(null, ImageImportMode.NEW_DOCUMENT)
        );
    }

    @Test
    public void modeIsRequired() {
        assertThrows(
                NullPointerException.class,
                () -> new ImageImportRequest(ImageImportSource.GALLERY, null)
        );
    }

    @Test
    public void supportsEverySourceInEveryMode() {
        for (ImageImportSource source : ImageImportSource.values()) {
            for (ImageImportMode mode : ImageImportMode.values()) {
                ImageImportRequest request = new ImageImportRequest(source, mode);

                assertEquals(source, request.getSource());
                assertEquals(mode, request.getMode());
            }
        }
    }
}
