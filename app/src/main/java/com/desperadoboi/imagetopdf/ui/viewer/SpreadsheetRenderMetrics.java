package com.desperadoboi.imagetopdf.ui.viewer;

import com.desperadoboi.imagetopdf.document.spreadsheet.SpreadsheetCellStyle;

final class SpreadsheetRenderMetrics {
    static final float TECHNICAL_MIN_TEXT_SIZE_PX = 0.5f;
    static final float DEFAULT_FIT_SHEET_THRESHOLD = 0.60f;

    private SpreadsheetRenderMetrics() {
    }

    static float effectiveFontSize(
            float workbookFontPx,
            float viewportScale,
            ZoomController.ZoomMode zoomMode,
            float readableMinimumPx
    ) {
        float safeFontSize = finitePositive(workbookFontPx, 1f);
        float safeScale = finitePositive(viewportScale, ZoomController.NORMAL_ZOOM);
        float scaledSize = safeFontSize * safeScale;
        if (ZoomController.isOverview(zoomMode)) {
            return Math.max(TECHNICAL_MIN_TEXT_SIZE_PX, scaledSize);
        }
        return Math.max(Math.max(0f, readableMinimumPx), scaledSize);
    }

    static int scaledPadding(
            int basePaddingPx,
            float viewportScale,
            ZoomController.ZoomMode zoomMode
    ) {
        float safeScale = finitePositive(viewportScale, ZoomController.NORMAL_ZOOM);
        float paddingScale = ZoomController.isOverview(zoomMode)
                ? safeScale
                : Math.max(ZoomController.MIN_ZOOM, safeScale);
        return Math.max(0, Math.round(Math.max(0, basePaddingPx) * paddingScale));
    }

    static int rowHeight(
            int workbookHeightPx,
            float viewportScale,
            ZoomController.ZoomMode zoomMode,
            boolean hidden,
            float measuredTextHeightPx,
            int verticalPaddingPx,
            int minimumManualHeightPx
    ) {
        if (hidden) return 0;
        float safeScale = finitePositive(viewportScale, ZoomController.NORMAL_ZOOM);
        int scaledWorkbookHeight = Math.max(
                1,
                Math.round(Math.max(1, workbookHeightPx) * safeScale)
        );
        if (ZoomController.isOverview(zoomMode)) return scaledWorkbookHeight;
        int measuredHeight = Math.max(
                0,
                (int) Math.ceil(Math.max(0f, measuredTextHeightPx))
                        + Math.max(0, verticalPaddingPx) * 2
        );
        return Math.max(
                scaledWorkbookHeight,
                Math.max(Math.max(0, minimumManualHeightPx), measuredHeight)
        );
    }

    static float lineHeight(float ascent, float descent, float leading) {
        return Math.max(0f, descent - ascent + Math.max(0f, leading));
    }

    static int visibleLineCount(float availableHeight, float lineHeight, int requestedLines) {
        if (availableHeight <= 0f || lineHeight <= 0f || requestedLines <= 0) return 0;
        return Math.max(
                0,
                Math.min(requestedLines, (int) Math.floor(availableHeight / lineHeight))
        );
    }

    static float fitFontSizeToHeight(
            float desiredFontSizePx,
            float measuredLineHeightPx,
            float availableHeightPx
    ) {
        if (desiredFontSizePx <= 0f || measuredLineHeightPx <= 0f || availableHeightPx <= 0f) {
            return 0f;
        }
        if (measuredLineHeightPx <= availableHeightPx) return desiredFontSizePx;
        float fittedSize = desiredFontSizePx * availableHeightPx / measuredLineHeightPx;
        return fittedSize >= TECHNICAL_MIN_TEXT_SIZE_PX ? fittedSize : 0f;
    }

    static float firstBaseline(
            float contentTop,
            float contentBottom,
            int lineCount,
            float ascent,
            float descent,
            float leading,
            SpreadsheetCellStyle.VerticalAlignment alignment
    ) {
        float lineHeight = lineHeight(ascent, descent, leading);
        float blockHeight = lineCount <= 0 ? 0f : lineHeight * lineCount;
        float blockTop;
        switch (alignment) {
            case TOP:
                blockTop = contentTop;
                break;
            case CENTER:
                blockTop = contentTop
                        + Math.max(0f, (contentBottom - contentTop - blockHeight) / 2f);
                break;
            case BOTTOM:
            default:
                blockTop = Math.max(contentTop, contentBottom - blockHeight);
                break;
        }
        return blockTop - ascent;
    }

    static ClipBounds cellClip(
            int width,
            int height,
            int horizontalPadding,
            int verticalPadding
    ) {
        float left = Math.max(0, horizontalPadding);
        float top = Math.max(0, verticalPadding);
        float right = Math.max(left, width - Math.max(0, horizontalPadding));
        float bottom = Math.max(top, height - Math.max(0, verticalPadding));
        return new ClipBounds(left, top, right, bottom);
    }

    static final class ClipBounds {
        final float left;
        final float top;
        final float right;
        final float bottom;

        ClipBounds(float left, float top, float right, float bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        float width() {
            return right - left;
        }

        float height() {
            return bottom - top;
        }

        boolean isEmpty() {
            return width() <= 0f || height() <= 0f;
        }
    }

    private static float finitePositive(float value, float fallback) {
        return Float.isFinite(value) && value > 0f ? value : fallback;
    }
}
