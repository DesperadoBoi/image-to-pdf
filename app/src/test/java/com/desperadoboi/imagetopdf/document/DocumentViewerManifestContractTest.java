package com.desperadoboi.imagetopdf.document;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class DocumentViewerManifestContractTest {
    private static final String ANDROID = "http://schemas.android.com/apk/res/android";
    private static final String XLSX =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String DOCX =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    @Test
    public void viewerRegistersOnlyNarrowWorkingOfficeMimes() throws Exception {
        Document manifest = parseManifest();
        Element viewer = findViewerActivity(manifest);
        Set<String> mimeTypes = new HashSet<>();
        NodeList data = viewer.getElementsByTagName("data");
        for (int index = 0; index < data.getLength(); index++) {
            mimeTypes.add(((Element) data.item(index)).getAttributeNS(ANDROID, "mimeType"));
        }
        assertTrue(mimeTypes.contains(XLSX));
        assertTrue(mimeTypes.contains(DOCX));
        assertFalse(mimeTypes.contains("application/vnd.ms-excel"));
        assertFalse(mimeTypes.contains("application/msword"));
        assertFalse(mimeTypes.contains(
                "application/vnd.ms-word.document.macroenabled.12"
        ));
        assertFalse(mimeTypes.contains("application/octet-stream"));
        assertFalse(mimeTypes.contains("*/*"));
        assertFalse(hasCategory(viewer, "android.intent.category.BROWSABLE"));
    }

    @Test
    public void manifestDoesNotRequestInternet() throws Exception {
        Document manifest = parseManifest();
        NodeList permissions = manifest.getElementsByTagName("uses-permission");
        for (int index = 0; index < permissions.getLength(); index++) {
            String name = ((Element) permissions.item(index)).getAttributeNS(ANDROID, "name");
            assertFalse("android.permission.INTERNET".equals(name));
            assertFalse("android.permission.WRITE_EXTERNAL_STORAGE".equals(name));
            assertFalse("android.permission.MANAGE_EXTERNAL_STORAGE".equals(name));
        }
    }

    private static boolean hasCategory(Element activity, String categoryName) {
        NodeList categories = activity.getElementsByTagName("category");
        for (int index = 0; index < categories.getLength(); index++) {
            if (categoryName.equals(
                    ((Element) categories.item(index)).getAttributeNS(ANDROID, "name")
            )) {
                return true;
            }
        }
        return false;
    }

    private static Element findViewerActivity(Document document) {
        NodeList activities = document.getElementsByTagName("activity");
        for (int index = 0; index < activities.getLength(); index++) {
            Element activity = (Element) activities.item(index);
            if (".ui.viewer.DocumentViewerActivity".equals(
                    activity.getAttributeNS(ANDROID, "name")
            )) {
                return activity;
            }
        }
        throw new AssertionError("DocumentViewerActivity is missing");
    }

    private static Document parseManifest() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "");
        factory.setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "");
        return factory.newDocumentBuilder().parse(
                repositoryRoot().resolve("app/src/main/AndroidManifest.xml").toFile()
        );
    }

    private static Path repositoryRoot() {
        Path current = Paths.get("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("app/src/main"))) return current;
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("app/src/main"))) return parent;
        throw new IllegalStateException("Repository root was not found from " + current);
    }
}
