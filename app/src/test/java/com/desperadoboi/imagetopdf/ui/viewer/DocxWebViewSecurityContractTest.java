package com.desperadoboi.imagetopdf.ui.viewer;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class DocxWebViewSecurityContractTest {
    @Test
    public void webViewDisablesActiveAndPersistentCapabilities() throws Exception {
        String source = controllerSource();

        assertTrue(source.contains("settings.setJavaScriptEnabled(false);"));
        assertTrue(source.contains("settings.setJavaScriptCanOpenWindowsAutomatically(false);"));
        assertTrue(source.contains("settings.setAllowFileAccess(false);"));
        assertTrue(source.contains("settings.setAllowContentAccess(false);"));
        assertTrue(source.contains("settings.setAllowFileAccessFromFileURLs(false);"));
        assertTrue(source.contains("settings.setAllowUniversalAccessFromFileURLs(false);"));
        assertTrue(source.contains("settings.setDatabaseEnabled(false);"));
        assertTrue(source.contains("settings.setDomStorageEnabled(false);"));
        assertTrue(source.contains("settings.setGeolocationEnabled(false);"));
        assertTrue(source.contains("WebSettings.MIXED_CONTENT_NEVER_ALLOW"));
        assertFalse(source.contains("addJavascriptInterface"));
        assertFalse(source.contains("evaluateJavascript"));
    }

    @Test
    public void networkAndExternalNavigationAreBlockedInsideWebView() throws Exception {
        String source = controllerSource();

        assertTrue(source.contains("settings.setBlockNetworkLoads(true);"));
        assertTrue(source.contains("settings.setBlockNetworkImage(true);"));
        assertTrue(source.contains("settings.setBuiltInZoomControls(true);"));
        assertTrue(source.contains("settings.setLoadWithOverviewMode(true);"));
        assertTrue(source.contains("settings.setTextZoom(100);"));
        assertTrue(source.contains("shouldInterceptRequest("));
        assertTrue(source.contains("return blockedResponse();"));
        assertTrue(source.contains("shouldOverrideUrlLoading("));
        assertTrue(source.contains("return true;"));
        assertTrue(source.contains("request.hasGesture()"));
    }

    @Test
    public void onlyExplicitHttpsIsForwardedToExternalHandler() throws Exception {
        String source = controllerSource();
        String activity = Files.readString(repositoryRoot().resolve(
                "app/src/main/java/com/desperadoboi/imagetopdf/ui/viewer/"
                        + "DocumentViewerActivity.java"
        ));

        assertTrue(source.contains("\"https\".equalsIgnoreCase(uri.getScheme())"));
        assertTrue(source.contains("linkHandler.openHttps(uri.toString())"));
        assertTrue(activity.contains("startActivity(new Intent(Intent.ACTION_VIEW, uri))"));
        assertTrue(activity.contains("catch (ActivityNotFoundException exception)"));
    }

    @Test
    public void generatedHtmlCspAllowsOnlyInlineCssAndDataImages() {
        String html = new DocxHtmlRenderer(
                image -> null,
                new DocxHtmlRenderer.Labels("", "", "")
        ).render(new com.desperadoboi.imagetopdf.document.word.WordDocumentModel(
                java.util.Collections.singletonList(
                        new com.desperadoboi.imagetopdf.document.word.WordParagraph(
                                java.util.Collections.emptyList(),
                                com.desperadoboi.imagetopdf.document.word
                                        .WordParagraphStyle.defaults(),
                                "",
                                com.desperadoboi.imagetopdf.document.word
                                        .WordParagraph.Role.BODY
                        )
                ),
                java.util.Collections.emptyList(),
                1,
                0,
                0
        )).getHtml();

        assertTrue(html.contains("default-src 'none'"));
        assertTrue(html.contains("img-src data:"));
        assertTrue(html.contains("script-src 'none'"));
        assertTrue(html.contains("connect-src 'none'"));
        assertFalse(html.contains("<script"));
    }

    private static String controllerSource() throws Exception {
        return Files.readString(repositoryRoot().resolve(
                "app/src/main/java/com/desperadoboi/imagetopdf/ui/viewer/"
                        + "DocxWebViewController.java"
        ));
    }

    private static Path repositoryRoot() {
        Path current = Paths.get("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("app/src/main"))) return current;
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("app/src/main"))) return parent;
        throw new IllegalStateException("Repository root was not found from " + current);
    }
}
