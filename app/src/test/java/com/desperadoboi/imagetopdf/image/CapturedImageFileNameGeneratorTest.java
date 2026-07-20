package com.desperadoboi.imagetopdf.image;

import org.junit.Test;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class CapturedImageFileNameGeneratorTest {
    @Test
    public void generatedNamesAreUnique() {
        CapturedImageFileNameGenerator generator = new CapturedImageFileNameGenerator();

        assertNotEquals(generator.createFileName(), generator.createFileName());
    }

    @Test
    public void generatedNameUsesJpgExtension() {
        CapturedImageFileNameGenerator generator = new CapturedImageFileNameGenerator();

        assertTrue(generator.createFileName().endsWith(".jpg"));
    }

    @Test
    public void invalidTokensAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> CapturedImageFileNameGenerator.buildFileName(null));
        assertThrows(IllegalArgumentException.class, () -> CapturedImageFileNameGenerator.buildFileName(""));
        assertThrows(IllegalArgumentException.class, () -> CapturedImageFileNameGenerator.buildFileName("../x"));
        assertThrows(IllegalArgumentException.class, () -> CapturedImageFileNameGenerator.buildFileName("x.jpg"));
    }
}
