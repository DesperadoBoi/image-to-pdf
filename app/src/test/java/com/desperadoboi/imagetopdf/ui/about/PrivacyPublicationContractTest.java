package com.desperadoboi.imagetopdf.ui.about;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

public final class PrivacyPublicationContractTest {
    private static final String DEVELOPER = "DesperadoBoi";
    private static final String EXPECTED_EMAIL = "mihaelkruspe@gmail.com";
    private static final String EXPECTED_RU_DATE = "22 июля 2026 года";
    private static final String EXPECTED_EN_DATE = "July 22, 2026";
    private static final String EMAIL_SUBJECT = "ImageToPDF — обратная связь";
    private static final String ROOT_URL =
            "https://desperadoboi.github.io/image-to-pdf/";
    private static final String EXPECTED_RU_URL = ROOT_URL + "privacy/";
    private static final String EXPECTED_EN_URL = EXPECTED_RU_URL + "en/";

    @Test
    public void resourceFactsMatchPublicationContract() throws Exception {
        Map<String, ResourceString> ru = readStrings(
                repositoryRoot().resolve("app/src/main/res/values/about_privacy_strings.xml")
        );
        Map<String, ResourceString> en = readStrings(
                repositoryRoot().resolve("app/src/main/res/values-en/strings.xml")
        );

        assertEquals(DEVELOPER, ru.get("developer_name").value);
        assertEquals(EXPECTED_EMAIL, ru.get("developer_email").value);
        assertEquals(EMAIL_SUBJECT, ru.get("developer_email_subject").value);
        assertEquals(EXPECTED_RU_DATE, ru.get("privacy_effective_date").value);
        assertEquals(EXPECTED_EN_DATE, en.get("privacy_effective_date").value);
        assertEquals(EXPECTED_RU_URL, ru.get("privacy_policy_url").value);
        assertEquals(EXPECTED_EN_URL, en.get("privacy_policy_url").value);

        assertContainsFacts(
                repositoryRoot().resolve("docs/PRIVACY_POLICY_RU.md"),
                EXPECTED_RU_DATE,
                EXPECTED_RU_URL
        );
        assertContainsFacts(
                repositoryRoot().resolve("docs/privacy/index.html"),
                EXPECTED_RU_DATE,
                EXPECTED_RU_URL
        );
        assertContainsFacts(
                repositoryRoot().resolve("docs/PRIVACY_POLICY_EN.md"),
                EXPECTED_EN_DATE,
                EXPECTED_EN_URL
        );
        assertContainsFacts(
                repositoryRoot().resolve("docs/privacy/en/index.html"),
                EXPECTED_EN_DATE,
                EXPECTED_EN_URL
        );
    }

    @Test
    public void localizedResourcesContainSameNewKeys() throws Exception {
        Map<String, ResourceString> ru = readStrings(
                repositoryRoot().resolve("app/src/main/res/values/about_privacy_strings.xml")
        );
        Map<String, ResourceString> en = readStrings(
                repositoryRoot().resolve("app/src/main/res/values-en/strings.xml")
        );
        Set<String> localizableRuKeys = new LinkedHashSet<>();
        for (Map.Entry<String, ResourceString> entry : ru.entrySet()) {
            if (entry.getValue().translatable) {
                localizableRuKeys.add(entry.getKey());
            }
        }

        assertEquals(localizableRuKeys, en.keySet());
    }

    @Test
    public void githubPagesPathsAndRelativeLinksMatchPublicUrls() throws Exception {
        Path docs = repositoryRoot().resolve("docs");
        Path rootPage = docs.resolve("index.html");
        Path ruPage = docs.resolve("privacy/index.html");
        Path enPage = docs.resolve("privacy/en/index.html");

        assertTrue(Files.isRegularFile(rootPage));
        assertTrue(Files.isRegularFile(ruPage));
        assertTrue(Files.isRegularFile(enPage));
        assertTrue(Files.isRegularFile(docs.resolve(".nojekyll")));

        String rootHtml = read(rootPage);
        String ruHtml = read(ruPage);
        String enHtml = read(enPage);
        assertTrue(rootHtml.contains("href=\"privacy/\""));
        assertTrue(rootHtml.contains("href=\"privacy/en/\""));
        assertTrue(rootHtml.contains("href=\"" + ROOT_URL + "\""));
        assertTrue(ruHtml.contains("href=\"en/\""));
        assertTrue(ruHtml.contains("href=\"" + EXPECTED_RU_URL + "\""));
        assertTrue(enHtml.contains("href=\"../\""));
        assertTrue(enHtml.contains("href=\"" + EXPECTED_EN_URL + "\""));
    }

    @Test
    public void privacyArtifactsContainNoPlaceholders() throws Exception {
        List<String> forbiddenTokens = Arrays.asList(
                "DEVELOPER_" + "NAME",
                "SUPPORT_" + "EMAIL",
                "EFFECTIVE_" + "DATE",
                "PRIVACY_" + "URL",
                "example" + ".com",
                "YOUR" + "_",
                "TO" + "DO",
                "T" + "BD"
        );
        for (Path path : privacyArtifacts()) {
            String content = read(path);
            for (String token : forbiddenTokens) {
                assertFalse(path + " contains " + token, content.contains(token));
            }
        }
    }

    @Test
    public void pagesRemainStaticAndSelfContained() throws Exception {
        for (Path page : Arrays.asList(
                repositoryRoot().resolve("docs/index.html"),
                repositoryRoot().resolve("docs/privacy/index.html"),
                repositoryRoot().resolve("docs/privacy/en/index.html")
        )) {
            String html = read(page).toLowerCase(Locale.ROOT);
            assertFalse(page + " contains JavaScript", html.contains("<script"));
            assertFalse(page + " contains a CDN", html.contains("cdn"));
            assertFalse(page + " contains external fonts", html.contains("fonts.googleapis"));
            assertFalse(page + " sets cookies", html.contains("document.cookie"));
            assertFalse(page + " contains Google Analytics", html.contains("google-analytics"));
            assertFalse(page + " contains analytics code", html.contains("gtag("));
            assertFalse(page + " contains images", html.contains("<img"));
        }
    }

    private static List<Path> privacyArtifacts() {
        Path root = repositoryRoot();
        return Arrays.asList(
                root.resolve("app/src/main/java/com/desperadoboi/imagetopdf/ui/about/AboutFragment.java"),
                root.resolve("app/src/main/res/layout/fragment_about.xml"),
                root.resolve("app/src/main/res/layout/fragment_privacy_policy.xml"),
                root.resolve("app/src/main/res/values/about_privacy_strings.xml"),
                root.resolve("app/src/main/res/values-en/strings.xml"),
                root.resolve("docs/index.html"),
                root.resolve("docs/privacy/index.html"),
                root.resolve("docs/privacy/en/index.html"),
                root.resolve("docs/PRIVACY_POLICY_RU.md"),
                root.resolve("docs/PRIVACY_POLICY_EN.md"),
                root.resolve("docs/PRIVACY_POLICY_PUBLISHING.md"),
                root.resolve("docs/RELEASE_CHECKLIST.md"),
                root.resolve("design/play-store/ASSET_CHECKLIST.md"),
                root.resolve("design/play-store/DATA_SAFETY_DRAFT.md")
        );
    }

    private static void assertContainsFacts(Path path, String date, String url) throws Exception {
        String content = read(path);
        assertTrue(path + " is missing the developer", content.contains(DEVELOPER));
        assertTrue(path + " is missing the email", content.contains(EXPECTED_EMAIL));
        assertTrue(path + " is missing the effective date", content.contains(date));
        assertTrue(path + " is missing the public URL", content.contains(url));
    }

    private static Map<String, ResourceString> readStrings(Path path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute(
                "http://javax.xml.XMLConstants/property/accessExternalDTD",
                ""
        );
        factory.setAttribute(
                "http://javax.xml.XMLConstants/property/accessExternalSchema",
                ""
        );
        Document document = factory.newDocumentBuilder().parse(path.toFile());
        NodeList nodes = document.getElementsByTagName("string");
        Map<String, ResourceString> strings = new LinkedHashMap<>();
        for (int index = 0; index < nodes.getLength(); index++) {
            Element element = (Element) nodes.item(index);
            strings.put(
                    element.getAttribute("name"),
                    new ResourceString(
                            element.getTextContent(),
                            !"false".equals(element.getAttribute("translatable"))
                    )
            );
        }
        return strings;
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path repositoryRoot() {
        Path current = Paths.get("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("app/src/main"))) {
            return current;
        }
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("app/src/main"))) {
            return parent;
        }
        throw new IllegalStateException("Repository root was not found from " + current);
    }

    private static final class ResourceString {
        private final String value;
        private final boolean translatable;

        private ResourceString(String value, boolean translatable) {
            this.value = value;
            this.translatable = translatable;
        }
    }
}
