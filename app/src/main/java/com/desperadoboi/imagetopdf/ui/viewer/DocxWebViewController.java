package com.desperadoboi.imagetopdf.ui.viewer;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.webkit.CookieManager;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.Nullable;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

final class DocxWebViewController {
    interface LinkHandler {
        void openHttps(String value);
    }

    interface RenderHandler {
        void onRendered();

        void onFailure();
    }

    private final WebView webView;
    private final LinkHandler linkHandler;
    private final RenderHandler renderHandler;
    private final GestureDetector doubleTapDetector;
    @Nullable private final Runnable scrollChanged;
    private boolean restoreApplied;
    private boolean documentLoadPending;
    private boolean renderFailureReported;
    private boolean destroyed;
    private float fitWidthScale = 1f;

    DocxWebViewController(
            WebView webView,
            LinkHandler linkHandler,
            RenderHandler renderHandler,
            @Nullable Runnable scrollChanged
    ) {
        this.webView = webView;
        this.linkHandler = linkHandler;
        this.renderHandler = renderHandler;
        this.scrollChanged = scrollChanged;
        this.doubleTapDetector = new GestureDetector(
                webView.getContext(),
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDown(MotionEvent event) {
                        return true;
                    }

                    @Override
                    public boolean onDoubleTap(MotionEvent event) {
                        toggleDoubleTapZoom();
                        return true;
                    }
                }
        );
        doubleTapDetector.setIsLongpressEnabled(false);
        configure();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configure() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setDatabaseEnabled(false);
        settings.setDomStorageEnabled(false);
        settings.setGeolocationEnabled(false);
        settings.setSaveFormData(false);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setBlockNetworkLoads(true);
        settings.setBlockNetworkImage(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setTextZoom(100);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(true);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            settings.setOffscreenPreRaster(false);
        }

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(false);
        cookieManager.setAcceptThirdPartyCookies(webView, false);
        webView.removeJavascriptInterface("searchBoxJavaBridge_");
        webView.removeJavascriptInterface("accessibility");
        webView.removeJavascriptInterface("accessibilityTraversal");
        webView.setWebViewClient(new LocalOnlyClient());
        webView.setOnTouchListener((view, event) -> {
            doubleTapDetector.onTouchEvent(event);
            return false;
        });
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            webView.setOnScrollChangeListener(
                    (view, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                        if (scrollChanged != null) scrollChanged.run();
                    }
            );
        }
    }

    void load(String html, @Nullable State restoredState) {
        if (destroyed) {
            reportRenderFailure();
            return;
        }
        restoreApplied = false;
        documentLoadPending = true;
        renderFailureReported = false;
        webView.stopLoading();
        webView.loadDataWithBaseURL(
                null,
                html == null ? "" : html,
                "text/html",
                StandardCharsets.UTF_8.name(),
                null
        );
        if (restoredState != null) {
            webView.setTag(restoredState);
        } else {
            webView.setTag(null);
        }
    }

    State captureState() {
        return new State(
                Math.max(0, webView.getScrollX()),
                Math.max(0, webView.getScrollY()),
                safeScale(webView.getScale())
        );
    }

    void goToTop() {
        if (destroyed) return;
        webView.scrollTo(0, 0);
    }

    boolean isAtTop() {
        return webView.getScrollY() <= 0;
    }

    void fitPageWidth() {
        if (destroyed) return;
        float currentScale = safeScale(webView.getScale());
        float factor = Math.max(0.01f, Math.min(100f, fitWidthScale / currentScale));
        if (Math.abs(factor - 1f) > 0.01f) webView.zoomBy(factor);
    }

    private void toggleDoubleTapZoom() {
        if (destroyed) return;
        float currentScale = safeScale(webView.getScale());
        float targetScale = currentScale > fitWidthScale * 1.2f
                ? fitWidthScale
                : fitWidthScale * 1.75f;
        float factor = Math.max(0.01f, Math.min(100f, targetScale / currentScale));
        if (Math.abs(factor - 1f) > 0.01f) webView.zoomBy(factor);
    }

    void clear() {
        if (destroyed) return;
        restoreApplied = false;
        documentLoadPending = false;
        renderFailureReported = false;
        webView.stopLoading();
        webView.setTag(null);
        webView.loadDataWithBaseURL(
                null,
                "<!doctype html><html><body></body></html>",
                "text/html",
                StandardCharsets.UTF_8.name(),
                null
        );
        webView.scrollTo(0, 0);
    }

    void destroy() {
        if (destroyed) return;
        destroyed = true;
        documentLoadPending = false;
        webView.stopLoading();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            webView.setOnScrollChangeListener(null);
        }
        webView.setOnTouchListener(null);
        webView.setWebViewClient(new WebViewClient());
        webView.removeAllViews();
        webView.destroy();
    }

    private float safeScale(float candidate) {
        return Float.isFinite(candidate) && candidate > 0f ? candidate : 1f;
    }

    private final class LocalOnlyClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            if (request.isForMainFrame() && request.hasGesture() && isHttps(uri)) {
                linkHandler.openHttps(uri.toString());
            }
            return true;
        }

        @SuppressWarnings("deprecation")
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            Uri uri = parse(url);
            if (isHttps(uri)) linkHandler.openHttps(uri.toString());
            return true;
        }

        @Nullable
        @Override
        public WebResourceResponse shouldInterceptRequest(
                WebView view,
                WebResourceRequest request
        ) {
            return responseFor(request.getUrl());
        }

        @SuppressWarnings("deprecation")
        @Nullable
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
            return responseFor(parse(url));
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            Uri uri = parse(url);
            if (uri != null && !isLocalDocumentScheme(uri)) {
                view.stopLoading();
            }
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            if (!restoreApplied) {
                restoreApplied = true;
                fitWidthScale = safeScale(view.getScale());
                Object tag = view.getTag();
                State state = tag instanceof State ? (State) tag : null;
                if (state != null) {
                    float currentScale = safeScale(view.getScale());
                    float targetScale = safeScale(state.scale);
                    float factor = Math.max(0.01f, Math.min(100f, targetScale / currentScale));
                    if (Math.abs(factor - 1f) > 0.01f) view.zoomBy(factor);
                    view.post(() -> view.scrollTo(state.scrollX, state.scrollY));
                }
            }
            if (documentLoadPending) {
                documentLoadPending = false;
                renderHandler.onRendered();
            }
        }

        @Override
        public void onReceivedError(
                WebView view,
                WebResourceRequest request,
                WebResourceError error
        ) {
            if (request.isForMainFrame() && documentLoadPending) reportRenderFailure();
        }

        @Override
        public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
            reportRenderFailure();
            destroyed = true;
            documentLoadPending = false;
            view.post(() -> {
                ViewParent parent = view.getParent();
                if (parent instanceof ViewGroup) ((ViewGroup) parent).removeView(view);
                view.destroy();
            });
            return true;
        }

        @Override
        public void onReceivedSslError(
                WebView view,
                SslErrorHandler handler,
                android.net.http.SslError error
        ) {
            handler.cancel();
        }

        @Nullable
        private WebResourceResponse responseFor(@Nullable Uri uri) {
            if (uri == null || isLocalDocumentScheme(uri)) return null;
            return blockedResponse();
        }

        private WebResourceResponse blockedResponse() {
            return new WebResourceResponse(
                    "text/plain",
                    StandardCharsets.UTF_8.name(),
                    new ByteArrayInputStream(new byte[0])
            );
        }

        @Nullable
        private Uri parse(@Nullable String value) {
            if (value == null) return null;
            try {
                return Uri.parse(value);
            } catch (RuntimeException ignored) {
                return null;
            }
        }

        private boolean isHttps(@Nullable Uri uri) {
            return uri != null
                    && "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && !uri.getHost().isEmpty();
        }

        private boolean isLocalDocumentScheme(Uri uri) {
            String scheme = uri.getScheme();
            return "data".equalsIgnoreCase(scheme) || "about".equalsIgnoreCase(scheme);
        }
    }

    private void reportRenderFailure() {
        if (renderFailureReported) return;
        renderFailureReported = true;
        documentLoadPending = false;
        renderHandler.onFailure();
    }

    static final class State {
        private final int scrollX;
        private final int scrollY;
        private final float scale;

        State(int scrollX, int scrollY, float scale) {
            this.scrollX = Math.max(0, scrollX);
            this.scrollY = Math.max(0, scrollY);
            this.scale = Float.isFinite(scale) && scale > 0f ? scale : 1f;
        }

        int getScrollX() {
            return scrollX;
        }

        int getScrollY() {
            return scrollY;
        }

        float getScale() {
            return scale;
        }
    }
}
