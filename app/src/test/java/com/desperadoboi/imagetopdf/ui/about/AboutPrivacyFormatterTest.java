package com.desperadoboi.imagetopdf.ui.about;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public final class AboutPrivacyFormatterTest {
    private static final String EMAIL = "mihaelkruspe@gmail.com";
    private static final String EMAIL_SUBJECT = "ImageToPDF — обратная связь";
    private static final String RU_URL =
            "https://desperadoboi.github.io/image-to-pdf/privacy/";
    private static final String EN_URL =
            "https://desperadoboi.github.io/image-to-pdf/privacy/en/";

    @Test
    public void resolveVersionNameUsesTrimmedValueAndSafeFallback() {
        assertEquals("1.0.0", AboutPrivacyFormatter.resolveVersionName(" 1.0.0 ", "unknown"));
        assertEquals("unknown", AboutPrivacyFormatter.resolveVersionName(null, "unknown"));
        assertEquals("unknown", AboutPrivacyFormatter.resolveVersionName("   ", "unknown"));
    }

    @Test
    public void createMailtoUriEncodesSubject() throws Exception {
        String mailtoUri = AboutPrivacyFormatter.createMailtoUri(
                EMAIL,
                EMAIL_SUBJECT
        );

        assertTrue(mailtoUri.startsWith("mailto:" + EMAIL + "?subject="));
        assertFalse(mailtoUri.contains(" "));
        URI parsed = URI.create(mailtoUri);
        assertEquals("mailto", parsed.getScheme());
        String encodedSubject = mailtoUri.substring(mailtoUri.indexOf("?subject=") + 9);
        assertEquals(
                EMAIL_SUBJECT,
                URLDecoder.decode(encodedSubject, StandardCharsets.UTF_8.name())
        );
    }

    @Test
    public void requireHttpsUriAcceptsPublishedPrivacyUrls() {
        assertEquals(RU_URL, AboutPrivacyFormatter.requireHttpsUri(RU_URL));
        assertEquals(EN_URL, AboutPrivacyFormatter.requireHttpsUri(EN_URL));
        assertThrows(
                IllegalArgumentException.class,
                () -> AboutPrivacyFormatter.requireHttpsUri(
                        "http://desperadoboi.github.io/image-to-pdf/privacy/"
                )
        );
    }
}
