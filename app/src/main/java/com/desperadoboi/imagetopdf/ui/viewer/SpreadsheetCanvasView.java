package com.desperadoboi.imagetopdf.ui.viewer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.PathEffect;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.accessibility.AccessibilityNodeProvider;
import android.widget.OverScroller;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

import com.desperadoboi.imagetopdf.R;
import com.desperadoboi.imagetopdf.document.spreadsheet.SpreadsheetBorder;
import com.desperadoboi.imagetopdf.document.spreadsheet.SpreadsheetCellStyle;
import com.desperadoboi.imagetopdf.document.spreadsheet.SpreadsheetMergedRange;

public final class SpreadsheetCanvasView extends View {
    private final SpreadsheetViewportTransform transform =
            new SpreadsheetViewportTransform();
    private final SpreadsheetRenderPlanner renderPlanner = new SpreadsheetRenderPlanner();
    private final GestureDetector gestureDetector;
    private final ScaleGestureDetector scaleGestureDetector;
    private final OverScroller flingScroller;
    private final SpreadsheetPaintCache paintCache;
    private final SpreadsheetTextLayoutCache textLayoutCache =
            new SpreadsheetTextLayoutCache();
    private final SpreadsheetSingleLineTextCache singleLineTextCache =
            new SpreadsheetSingleLineTextCache();
    private final SpreadsheetCanvasAccessibilityHelper accessibilityHelper;
    private final SpreadsheetRenderStats renderStats = new SpreadsheetRenderStats();
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint headerFillPaint = new Paint();
    private final Paint rowHeaderFillPaint = new Paint();
    private final Paint cornerFillPaint = new Paint();
    private final TextPaint headerTextPaint =
            new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint.FontMetrics reusableFontMetrics = new Paint.FontMetrics();
    private final RectF reusableRect = new RectF();
    private final Rect reusableAccessibilityRect = new Rect();
    private final PathEffect dashedEffect;
    private final PathEffect dottedEffect;

    private final int defaultCellColor;
    private final int gridColor;
    private final float density;
    private final int rowHeaderWidth;
    private final int columnHeaderHeight;
    private final int baseCellPadding;
    private final float defaultCellTextSize;
    private final boolean debugRenderStatsEnabled;

    @Nullable private SpreadsheetCanvasModel model;
    @Nullable private SpreadsheetViewportState pendingState;
    @Nullable private OnZoomChangeListener zoomChangeListener;
    private ZoomController.ZoomMode zoomMode = ZoomController.ZoomMode.ZOOM_100;
    private float targetScale = ZoomController.NORMAL_ZOOM;
    private boolean scaling;
    private boolean gestureContainedScale;
    private boolean skipNextPan;
    private int lastFlingX;
    private int lastFlingY;
    private int[] mergedDrawStamps = new int[0];
    private int mergedDrawGeneration = 1;

    public SpreadsheetCanvasView(Context context) {
        this(context, null);
    }

    public SpreadsheetCanvasView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SpreadsheetCanvasView(
            Context context,
            @Nullable AttributeSet attrs,
            int defStyleAttr
    ) {
        super(context, attrs, defStyleAttr);
        density = getResources().getDisplayMetrics().density;
        float scaledDensity = getResources().getDisplayMetrics().scaledDensity;
        rowHeaderWidth = getResources().getDimensionPixelSize(
                R.dimen.viewer_row_header_width
        );
        columnHeaderHeight = getResources().getDimensionPixelSize(
                R.dimen.viewer_spreadsheet_header_height
        );
        baseCellPadding = getResources().getDimensionPixelSize(R.dimen.viewer_cell_padding);
        defaultCellTextSize = getResources().getDimension(R.dimen.viewer_cell_text_size);
        debugRenderStatsEnabled = (context.getApplicationInfo().flags
                & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        defaultCellColor = ContextCompat.getColor(context, R.color.viewer_document_surface);
        int defaultTextColor = ContextCompat.getColor(context, R.color.viewer_document_text);
        gridColor = ContextCompat.getColor(context, R.color.viewer_table_grid);

        paintCache = new SpreadsheetPaintCache(
                defaultCellColor,
                defaultTextColor,
                defaultCellTextSize,
                scaledDensity
        );
        headerFillPaint.setColor(ContextCompat.getColor(
                context,
                R.color.viewer_table_column_header
        ));
        rowHeaderFillPaint.setColor(ContextCompat.getColor(
                context,
                R.color.viewer_table_row_header
        ));
        cornerFillPaint.setColor(gridColor);
        headerTextPaint.setColor(defaultTextColor);
        headerTextPaint.setTextSize(getResources().getDimension(R.dimen.viewer_cell_text_size));
        headerTextPaint.setTypeface(android.graphics.Typeface.create(
                "sans-serif-medium",
                android.graphics.Typeface.NORMAL
        ));
        headerTextPaint.setTextAlign(Paint.Align.CENTER);
        borderPaint.setStyle(Paint.Style.STROKE);
        dashedEffect = new DashPathEffect(new float[]{4f * density, 3f * density}, 0f);
        dottedEffect = new DashPathEffect(new float[]{density, 2f * density}, 0f);

        flingScroller = new OverScroller(context);
        scaleGestureDetector = new ScaleGestureDetector(context, new ScaleListener());
        gestureDetector = new GestureDetector(context, new GestureListener());
        accessibilityHelper = new SpreadsheetCanvasAccessibilityHelper(this);
        setClickable(true);
        setFocusable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    void submit(SpreadsheetCanvasModel model, SpreadsheetViewportState state) {
        stopMotion();
        this.model = model;
        paintCache.clear();
        textLayoutCache.clear();
        singleLineTextCache.clear();
        mergedDrawStamps = new int[model.getMergedCellIndex().getRangeCount()];
        mergedDrawGeneration = 1;
        pendingState = state;
        zoomMode = state.getZoomMode();
        targetScale = state.getScale();
        updateTransformBounds();
        if (getWidth() > 0 && getHeight() > 0) {
            restorePendingState();
        } else {
            requestLayout();
        }
        accessibilityHelper.invalidateRoot();
        invalidate();
    }

    void clear() {
        stopMotion();
        model = null;
        pendingState = null;
        zoomMode = ZoomController.ZoomMode.ZOOM_100;
        targetScale = ZoomController.NORMAL_ZOOM;
        transform.setBounds(0f, 0f, 0f, 0f);
        transform.set(
                ZoomController.NORMAL_ZOOM,
                0f,
                0f,
                ZoomController.ZoomMode.ZOOM_100
        );
        paintCache.clear();
        textLayoutCache.clear();
        singleLineTextCache.clear();
        mergedDrawStamps = new int[0];
        accessibilityHelper.invalidateRoot();
        invalidate();
    }

    SpreadsheetViewportState captureState() {
        if (model == null) return SpreadsheetViewportState.initialNormal();
        return SpreadsheetViewportState.positioned(
                transform.getScale(),
                transform.getOffsetX(),
                transform.getOffsetY(),
                transform.getCenterSheetX(),
                transform.getCenterSheetY(),
                zoomMode
        );
    }

    void fitToWidth() {
        if (model == null || getContentWidth() <= 0f) return;
        stopMotion();
        float scale = transform.fitWidthScale();
        transform.zoomAround(
                scale,
                getContentWidth() / 2f,
                getContentHeight() / 2f,
                ZoomController.ZoomMode.FIT_WIDTH
        );
        transform.set(
                scale,
                0f,
                transform.getOffsetY(),
                ZoomController.ZoomMode.FIT_WIDTH
        );
        zoomMode = ZoomController.ZoomMode.FIT_WIDTH;
        targetScale = scale;
        viewportChanged();
        notifyZoomChanged(true, true);
    }

    void fitToSheet() {
        if (model == null || getContentWidth() <= 0f || getContentHeight() <= 0f) return;
        stopMotion();
        float scale = transform.fitSheetScale();
        transform.set(scale, 0f, 0f, ZoomController.ZoomMode.FIT_SHEET);
        zoomMode = ZoomController.ZoomMode.FIT_SHEET;
        targetScale = scale;
        viewportChanged();
        notifyZoomChanged(true, true);
    }

    void zoomToNormal() {
        if (model == null) return;
        stopMotion();
        transform.zoomAround(
                ZoomController.NORMAL_ZOOM,
                getContentWidth() / 2f,
                getContentHeight() / 2f,
                ZoomController.ZoomMode.ZOOM_100
        );
        zoomMode = ZoomController.ZoomMode.ZOOM_100;
        targetScale = ZoomController.NORMAL_ZOOM;
        viewportChanged();
        notifyZoomChanged(true, true);
    }

    ZoomController.ZoomMode getZoomMode() {
        return zoomMode;
    }

    void setOnZoomChangeListener(@Nullable OnZoomChangeListener listener) {
        zoomChangeListener = listener;
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (model == null) return;
        float centerX = transform.getCenterSheetX();
        float centerY = transform.getCenterSheetY();
        boolean hadOldViewport = oldWidth > 0 && oldHeight > 0;
        updateTransformBounds();
        if (pendingState != null) {
            restorePendingState();
        } else if (hadOldViewport) {
            float scale = scaleForCurrentMode();
            transform.restoreAroundCenter(scale, centerX, centerY, zoomMode);
            targetScale = scale;
        }
        accessibilityHelper.invalidateRoot();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        SpreadsheetCanvasModel currentModel = model;
        if (currentModel == null) {
            canvas.drawColor(defaultCellColor);
            return;
        }
        long started = debugRenderStatsEnabled ? SystemClock.elapsedRealtimeNanos() : 0L;
        SpreadsheetRenderPlan plan = renderPlanner.plan(
                currentModel.getGeometry(),
                transform
        );
        if (debugRenderStatsEnabled) renderStats.reset(plan);
        canvas.drawColor(defaultCellColor);
        int bodySave = canvas.save();
        canvas.clipRect(rowHeaderWidth, columnHeaderHeight, getWidth(), getHeight());
        drawFills(canvas, currentModel, plan);
        drawBorders(canvas, currentModel, plan);
        drawText(canvas, currentModel, plan);
        canvas.restoreToCount(bodySave);
        drawHeaders(canvas, currentModel, plan);
        if (debugRenderStatsEnabled) {
            renderStats.textLayoutCacheHits = textLayoutCache.getHits();
            renderStats.textLayoutCacheMisses = textLayoutCache.getMisses();
            renderStats.drawDurationNanos = SystemClock.elapsedRealtimeNanos() - started;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (model == null) return super.onTouchEvent(event);
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            stopMotion();
            gestureContainedScale = false;
            skipNextPan = false;
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
        }
        scaleGestureDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
            if (action == MotionEvent.ACTION_CANCEL) scaling = false;
            skipNextPan = false;
        }
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    public void computeScroll() {
        if (!flingScroller.computeScrollOffset()) return;
        int currentX = flingScroller.getCurrX();
        int currentY = flingScroller.getCurrY();
        transform.panByScreen(currentX - lastFlingX, currentY - lastFlingY);
        lastFlingX = currentX;
        lastFlingY = currentY;
        viewportChanged();
        ViewCompat.postInvalidateOnAnimation(this);
    }

    @Override
    public AccessibilityNodeProvider getAccessibilityNodeProvider() {
        return accessibilityHelper;
    }

    @Override
    public boolean dispatchHoverEvent(MotionEvent event) {
        return accessibilityHelper.dispatchHoverEvent(event) || super.dispatchHoverEvent(event);
    }

    @Nullable
    SpreadsheetCanvasModel getCanvasModel() {
        return model;
    }

    SpreadsheetRenderPlan getAccessibilityRenderPlan() {
        SpreadsheetCanvasModel currentModel = model;
        return currentModel == null
                ? new SpreadsheetRenderPlan(
                        SpreadsheetVisibleRange.EMPTY,
                        SpreadsheetLevelOfDetailPolicy.Detail.OVERVIEW,
                        0,
                        0
                )
                : renderPlanner.plan(currentModel.getGeometry(), transform, 0);
    }

    int getRowHeaderWidthPx() {
        return rowHeaderWidth;
    }

    int getColumnHeaderHeightPx() {
        return columnHeaderHeight;
    }

    void getCellBoundsOnScreen(int row, int column, Rect outBounds) {
        SpreadsheetCanvasModel currentModel = model;
        if (currentModel == null) {
            outBounds.setEmpty();
            return;
        }
        SpreadsheetMergedRange range =
                currentModel.getMergedCellIndex().getRangeAt(row, column);
        if (range == null) {
            setScreenBounds(
                    currentModel.getGeometry().getColumnLeft(column),
                    currentModel.getGeometry().getRowTop(row),
                    currentModel.getGeometry().getColumnRight(column),
                    currentModel.getGeometry().getRowBottom(row)
            );
        } else {
            setScreenBounds(
                    currentModel.getGeometry().getColumnLeft(range.getFirstColumn()),
                    currentModel.getGeometry().getRowTop(range.getFirstRow()),
                    currentModel.getGeometry().getColumnRight(range.getLastColumn()),
                    currentModel.getGeometry().getRowBottom(range.getLastRow())
            );
        }
        reusableAccessibilityRect.set(
                Math.round(reusableRect.left),
                Math.round(reusableRect.top),
                Math.round(reusableRect.right),
                Math.round(reusableRect.bottom)
        );
        reusableAccessibilityRect.intersect(0, 0, getWidth(), getHeight());
        outBounds.set(reusableAccessibilityRect);
    }

    void getRowHeaderBoundsOnScreen(int row, Rect outBounds) {
        SpreadsheetCanvasModel currentModel = model;
        if (currentModel == null) {
            outBounds.setEmpty();
            return;
        }
        float top = bodyScreenY(currentModel.getGeometry().getRowTop(row));
        float bottom = bodyScreenY(currentModel.getGeometry().getRowBottom(row));
        outBounds.set(0, Math.round(top), rowHeaderWidth, Math.round(bottom));
        outBounds.intersect(0, 0, getWidth(), getHeight());
    }

    void getColumnHeaderBoundsOnScreen(int column, Rect outBounds) {
        SpreadsheetCanvasModel currentModel = model;
        if (currentModel == null) {
            outBounds.setEmpty();
            return;
        }
        float left = bodyScreenX(currentModel.getGeometry().getColumnLeft(column));
        float right = bodyScreenX(currentModel.getGeometry().getColumnRight(column));
        outBounds.set(Math.round(left), 0, Math.round(right), columnHeaderHeight);
        outBounds.intersect(0, 0, getWidth(), getHeight());
    }

    int hitTestCell(float screenX, float screenY) {
        SpreadsheetCanvasModel currentModel = model;
        if (currentModel == null
                || screenX < rowHeaderWidth
                || screenY < columnHeaderHeight) {
            return -1;
        }
        float sheetX = transform.getOffsetX()
                + (screenX - rowHeaderWidth) / transform.getScale();
        float sheetY = transform.getOffsetY()
                + (screenY - columnHeaderHeight) / transform.getScale();
        int row = currentModel.getGeometry().rowAt(sheetY);
        int column = currentModel.getGeometry().columnAt(sheetX);
        if (row < 0 || column < 0) return -1;
        SpreadsheetMergedRange range =
                currentModel.getMergedCellIndex().getRangeAt(row, column);
        if (range != null) {
            row = range.getFirstRow();
            column = range.getFirstColumn();
        }
        return SpreadsheetAccessibilityModel.cellId(
                row,
                column,
                currentModel.getGeometry().getColumnCount()
        );
    }

    int hitTestRowHeader(float screenX, float screenY) {
        SpreadsheetCanvasModel currentModel = model;
        if (currentModel == null
                || screenX < 0f
                || screenX >= rowHeaderWidth
                || screenY < columnHeaderHeight) {
            return -1;
        }
        float sheetY = transform.getOffsetY()
                + (screenY - columnHeaderHeight) / transform.getScale();
        return currentModel.getGeometry().rowAt(sheetY);
    }

    int hitTestColumnHeader(float screenX, float screenY) {
        SpreadsheetCanvasModel currentModel = model;
        if (currentModel == null
                || screenY < 0f
                || screenY >= columnHeaderHeight
                || screenX < rowHeaderWidth) {
            return -1;
        }
        float sheetX = transform.getOffsetX()
                + (screenX - rowHeaderWidth) / transform.getScale();
        return currentModel.getGeometry().columnAt(sheetX);
    }

    boolean performAccessibilityViewportAction(int action) {
        if (model == null) return false;
        if (action == SpreadsheetCanvasAccessibilityHelper.ACTION_ZOOM_100) {
            zoomToNormal();
            return true;
        }
        if (action == SpreadsheetCanvasAccessibilityHelper.ACTION_FIT_WIDTH) {
            fitToWidth();
            return true;
        }
        if (action == SpreadsheetCanvasAccessibilityHelper.ACTION_FIT_SHEET) {
            fitToSheet();
            return true;
        }
        float horizontal = getContentWidth() * 0.8f;
        float vertical = getContentHeight() * 0.8f;
        if (action == SpreadsheetCanvasAccessibilityHelper.ACTION_SCROLL_LEFT) {
            transform.panByScreen(-horizontal, 0f);
        } else if (action
                == SpreadsheetCanvasAccessibilityHelper.ACTION_SCROLL_RIGHT) {
            transform.panByScreen(horizontal, 0f);
        } else if (action
                == SpreadsheetCanvasAccessibilityHelper.ACTION_SCROLL_UP) {
            transform.panByScreen(0f, -vertical);
        } else if (action
                == SpreadsheetCanvasAccessibilityHelper.ACTION_SCROLL_DOWN) {
            transform.panByScreen(0f, vertical);
        } else {
            return false;
        }
        viewportChanged();
        return true;
    }

    private void restorePendingState() {
        SpreadsheetViewportState state = pendingState;
        if (state == null || model == null || getContentWidth() <= 0f || getContentHeight() <= 0f) {
            return;
        }
        pendingState = null;
        zoomMode = state.getZoomMode();
        float scale = scaleForMode(zoomMode, state.getScale());
        transform.set(scale, state.getHorizontalOffset(), state.getVerticalOffset(), zoomMode);
        if (state.hasViewportPosition()) {
            transform.restoreAroundCenter(
                    scale,
                    state.getCenterContentX(),
                    state.getCenterContentY(),
                    zoomMode
            );
        }
        targetScale = scale;
    }

    private void updateTransformBounds() {
        if (model == null) return;
        transform.setBounds(
                getContentWidth(),
                getContentHeight(),
                model.getGeometry().getSheetWidth(),
                model.getGeometry().getSheetHeight()
        );
    }

    private float scaleForCurrentMode() {
        return scaleForMode(zoomMode, transform.getScale());
    }

    private float scaleForMode(ZoomController.ZoomMode mode, float storedScale) {
        if (mode == ZoomController.ZoomMode.ZOOM_100) return ZoomController.NORMAL_ZOOM;
        if (mode == ZoomController.ZoomMode.FIT_WIDTH) return transform.fitWidthScale();
        if (mode == ZoomController.ZoomMode.FIT_SHEET) return transform.fitSheetScale();
        return ZoomController.clampZoom(storedScale);
    }

    private void drawFills(
            Canvas canvas,
            SpreadsheetCanvasModel currentModel,
            SpreadsheetRenderPlan plan
    ) {
        SpreadsheetVisibleRange range = plan.getVisibleRange();
        if (range.isEmpty()) return;
        nextMergedDrawGeneration();
        SpreadsheetGeometry geometry = currentModel.getGeometry();
        SpreadsheetMergedCellIndex mergedIndex = currentModel.getMergedCellIndex();
        for (int row = range.getFirstRow(); row <= range.getLastRow(); row++) {
            if (geometry.isRowHidden(row)) continue;
            for (int column = range.getFirstColumn();
                    column <= range.getLastColumn();
                    column++) {
                if (geometry.isColumnHidden(column)) continue;
                int mergedId = mergedIndex.getRangeId(row, column);
                int styleRow = row;
                int styleColumn = column;
                if (mergedId >= 0) {
                    if (wasMergedDrawn(mergedId)) continue;
                    markMergedDrawn(mergedId);
                    SpreadsheetMergedRange merged = mergedIndex.getRange(mergedId);
                    styleRow = merged.getFirstRow();
                    styleColumn = merged.getFirstColumn();
                    setScreenBounds(
                            geometry.getColumnLeft(merged.getFirstColumn()),
                            geometry.getRowTop(merged.getFirstRow()),
                            geometry.getColumnRight(merged.getLastColumn()),
                            geometry.getRowBottom(merged.getLastRow())
                    );
                } else {
                    setCellScreenBounds(geometry, row, column);
                }
                SpreadsheetCellStyle style = currentModel.getStyle(styleRow, styleColumn);
                if (style.getFillColor() != null && intersectsBody(reusableRect)) {
                    canvas.drawRect(reusableRect, paintCache.get(style).fillPaint);
                }
            }
        }
    }

    private void drawBorders(
            Canvas canvas,
            SpreadsheetCanvasModel currentModel,
            SpreadsheetRenderPlan plan
    ) {
        SpreadsheetVisibleRange range = plan.getVisibleRange();
        if (range.isEmpty()) return;
        nextMergedDrawGeneration();
        SpreadsheetGeometry geometry = currentModel.getGeometry();
        SpreadsheetMergedCellIndex mergedIndex = currentModel.getMergedCellIndex();
        for (int row = range.getFirstRow(); row <= range.getLastRow(); row++) {
            if (geometry.isRowHidden(row)) continue;
            for (int column = range.getFirstColumn();
                    column <= range.getLastColumn();
                    column++) {
                if (geometry.isColumnHidden(column)) continue;
                int mergedId = mergedIndex.getRangeId(row, column);
                SpreadsheetCellStyle style;
                boolean mergedCell = mergedId >= 0;
                if (mergedCell) {
                    if (wasMergedDrawn(mergedId)) continue;
                    markMergedDrawn(mergedId);
                    SpreadsheetMergedRange merged = mergedIndex.getRange(mergedId);
                    style = currentModel.getStyle(
                            merged.getFirstRow(),
                            merged.getFirstColumn()
                    );
                    setScreenBounds(
                            geometry.getColumnLeft(merged.getFirstColumn()),
                            geometry.getRowTop(merged.getFirstRow()),
                            geometry.getColumnRight(merged.getLastColumn()),
                            geometry.getRowBottom(merged.getLastRow())
                    );
                } else {
                    style = currentModel.getStyle(row, column);
                    setCellScreenBounds(geometry, row, column);
                }
                if (!intersectsBody(reusableRect)) continue;
                drawCellBorders(canvas, reusableRect, style, mergedCell);
            }
        }
    }

    private void drawText(
            Canvas canvas,
            SpreadsheetCanvasModel currentModel,
            SpreadsheetRenderPlan plan
    ) {
        SpreadsheetVisibleRange range = plan.getVisibleRange();
        if (range.isEmpty()) return;
        nextMergedDrawGeneration();
        SpreadsheetGeometry geometry = currentModel.getGeometry();
        SpreadsheetMergedCellIndex mergedIndex = currentModel.getMergedCellIndex();
        for (int row = range.getFirstRow(); row <= range.getLastRow(); row++) {
            if (geometry.isRowHidden(row)) continue;
            for (int column = range.getFirstColumn();
                    column <= range.getLastColumn();
                    column++) {
                if (geometry.isColumnHidden(column)) continue;
                int mergedId = mergedIndex.getRangeId(row, column);
                int textRow = row;
                int textColumn = column;
                if (mergedId >= 0) {
                    if (wasMergedDrawn(mergedId)) continue;
                    markMergedDrawn(mergedId);
                    SpreadsheetMergedRange merged = mergedIndex.getRange(mergedId);
                    textRow = merged.getFirstRow();
                    textColumn = merged.getFirstColumn();
                    setScreenBounds(
                            geometry.getColumnLeft(merged.getFirstColumn()),
                            geometry.getRowTop(merged.getFirstRow()),
                            geometry.getColumnRight(merged.getLastColumn()),
                            geometry.getRowBottom(merged.getLastRow())
                    );
                } else {
                    setCellScreenBounds(geometry, row, column);
                }
                String text = currentModel.getValue(textRow, textColumn);
                if (text.isEmpty() || !intersectsBody(reusableRect)) continue;
                SpreadsheetCellStyle style = currentModel.getStyle(textRow, textColumn);
                if (!SpreadsheetLevelOfDetailPolicy.shouldDrawText(
                        transform.getScale(),
                        reusableRect.width(),
                        reusableRect.height(),
                        style.isWrapText(),
                        scaling
                )) {
                    if (debugRenderStatsEnabled) renderStats.skippedTextCells++;
                    continue;
                }
                drawCellText(
                        canvas,
                        currentModel,
                        textRow,
                        textColumn,
                        text,
                        style,
                        reusableRect
                );
                if (debugRenderStatsEnabled) renderStats.drawnTextCells++;
            }
        }
    }

    private void drawCellText(
            Canvas canvas,
            SpreadsheetCanvasModel currentModel,
            int row,
            int column,
            String text,
            SpreadsheetCellStyle style,
            RectF bounds
    ) {
        SpreadsheetPaintCache.StylePaints paints = paintCache.get(style);
        TextPaint textPaint = paints.textPaint;
        textPaint.setTextSize(paints.baseTextSizePx * transform.getScale());
        float padding = clamp(
                baseCellPadding * transform.getScale(),
                2f * density,
                24f * density
        );
        float verticalPadding = Math.max(density, padding / 3f);
        float contentLeft = SpreadsheetTextClipPolicy.contentStart(
                bounds.left,
                bounds.right,
                padding
        );
        float contentTop = SpreadsheetTextClipPolicy.contentStart(
                bounds.top,
                bounds.bottom,
                verticalPadding
        );
        float contentRight = SpreadsheetTextClipPolicy.contentEnd(
                bounds.left,
                bounds.right,
                padding
        );
        float contentBottom = SpreadsheetTextClipPolicy.contentEnd(
                bounds.top,
                bounds.bottom,
                verticalPadding
        );
        if (!SpreadsheetTextClipPolicy.isDrawable(contentLeft, contentRight)
                || !SpreadsheetTextClipPolicy.isDrawable(contentTop, contentBottom)) {
            return;
        }

        int saveCount = canvas.save();
        canvas.clipRect(contentLeft, contentTop, contentRight, contentBottom);
        float availableWidth = contentRight - contentLeft;
        float availableHeight = contentBottom - contentTop;
        boolean needsWrap = style.isWrapText()
                && (text.indexOf('\n') >= 0 || textPaint.measureText(text) > availableWidth);
        textPaint.getFontMetrics(reusableFontMetrics);
        Paint.FontMetrics metrics = reusableFontMetrics;
        float lineHeight = Math.max(1f, metrics.descent - metrics.ascent + metrics.leading);
        if (style.isWrapText() && SpreadsheetLevelOfDetailPolicy.shouldBuildWrappedLayout(
                transform.getScale(),
                availableWidth,
                availableHeight,
                scaling,
                needsWrap
        )) {
            int maximumLines = Math.max(1, (int) Math.floor(availableHeight / lineHeight));
            Layout.Alignment alignment = layoutAlignment(style, text);
            StaticLayout layout = textLayoutCache.getOrCreate(
                    currentModel.getSheetId(),
                    row,
                    column,
                    text,
                    paintCache.styleKey(style),
                    Math.max(1, (int) Math.floor(availableWidth)),
                    maximumLines,
                    transform.getScale(),
                    textPaint,
                    alignment
            );
            float layoutTop = verticalBlockTop(
                    contentTop,
                    contentBottom,
                    Math.min(availableHeight, layout.getHeight()),
                    style.getVerticalAlignment()
            );
            canvas.translate(contentLeft, layoutTop);
            layout.draw(canvas);
        } else {
            String singleLineText = singleLineTextCache.get(text);
            float textWidth = textPaint.measureText(singleLineText);
            float x = horizontalTextPosition(
                    style,
                    singleLineText,
                    contentLeft,
                    contentRight,
                    textWidth
            );
            float baseline = verticalBaseline(
                    contentTop,
                    contentBottom,
                    metrics,
                    style.getVerticalAlignment()
            );
            canvas.drawText(singleLineText, x, baseline, textPaint);
        }
        canvas.restoreToCount(saveCount);
    }

    private void drawHeaders(
            Canvas canvas,
            SpreadsheetCanvasModel currentModel,
            SpreadsheetRenderPlan plan
    ) {
        SpreadsheetVisibleRange range = plan.getVisibleRange();
        canvas.drawRect(0f, 0f, getWidth(), columnHeaderHeight, headerFillPaint);
        canvas.drawRect(0f, columnHeaderHeight, rowHeaderWidth, getHeight(), rowHeaderFillPaint);
        SpreadsheetGeometry geometry = currentModel.getGeometry();
        headerTextPaint.getFontMetrics(reusableFontMetrics);
        Paint.FontMetrics metrics = reusableFontMetrics;
        float centeredBaselineOffset = -(metrics.ascent + metrics.descent) / 2f;
        if (!range.isEmpty()) {
            for (int column = range.getFirstColumn();
                    column <= range.getLastColumn();
                    column++) {
                if (geometry.isColumnHidden(column)) continue;
                float left = bodyScreenX(geometry.getColumnLeft(column));
                float right = bodyScreenX(geometry.getColumnRight(column));
                if (right <= rowHeaderWidth || left >= getWidth()) continue;
                reusableRect.set(left, 0f, right, columnHeaderHeight);
                canvas.drawRect(reusableRect, headerFillPaint);
                drawGridRect(canvas, reusableRect);
                canvas.drawText(
                        currentModel.getColumnLabel(column),
                        (left + right) / 2f,
                        columnHeaderHeight / 2f + centeredBaselineOffset,
                        headerTextPaint
                );
            }
            for (int row = range.getFirstRow(); row <= range.getLastRow(); row++) {
                if (geometry.isRowHidden(row)) continue;
                float top = bodyScreenY(geometry.getRowTop(row));
                float bottom = bodyScreenY(geometry.getRowBottom(row));
                if (bottom <= columnHeaderHeight || top >= getHeight()) continue;
                reusableRect.set(0f, top, rowHeaderWidth, bottom);
                canvas.drawRect(reusableRect, rowHeaderFillPaint);
                drawGridRect(canvas, reusableRect);
                canvas.drawText(
                        currentModel.getRowLabel(row),
                        rowHeaderWidth / 2f,
                        (top + bottom) / 2f + centeredBaselineOffset,
                        headerTextPaint
                );
            }
        }
        reusableRect.set(0f, 0f, rowHeaderWidth, columnHeaderHeight);
        canvas.drawRect(reusableRect, cornerFillPaint);
        drawGridRect(canvas, reusableRect);
    }

    private void drawCellBorders(
            Canvas canvas,
            RectF bounds,
            SpreadsheetCellStyle style,
            boolean merged
    ) {
        drawBorderSide(
                canvas,
                style.getLeftBorder(),
                bounds.left,
                bounds.top,
                bounds.left,
                bounds.bottom,
                merged,
                density * 2f,
                0f
        );
        drawBorderSide(
                canvas,
                style.getTopBorder(),
                bounds.left,
                bounds.top,
                bounds.right,
                bounds.top,
                merged,
                0f,
                density * 2f
        );
        drawBorderSide(
                canvas,
                style.getRightBorder(),
                bounds.right,
                bounds.top,
                bounds.right,
                bounds.bottom,
                true,
                -density * 2f,
                0f
        );
        drawBorderSide(
                canvas,
                style.getBottomBorder(),
                bounds.left,
                bounds.bottom,
                bounds.right,
                bounds.bottom,
                true,
                0f,
                -density * 2f
        );
    }

    private void drawBorderSide(
            Canvas canvas,
            SpreadsheetBorder border,
            float startX,
            float startY,
            float endX,
            float endY,
            boolean gridFallback,
            float doubleInsetX,
            float doubleInsetY
    ) {
        SpreadsheetBorder.Style style = border.getStyle();
        if (style == SpreadsheetBorder.Style.NONE && !gridFallback) return;
        borderPaint.setStrokeWidth(borderWidth(style));
        borderPaint.setColor(style == SpreadsheetBorder.Style.NONE
                ? gridColor
                : border.getColor());
        borderPaint.setPathEffect(style == SpreadsheetBorder.Style.DASHED
                ? dashedEffect
                : style == SpreadsheetBorder.Style.DOTTED ? dottedEffect : null);
        canvas.drawLine(startX, startY, endX, endY, borderPaint);
        if (style == SpreadsheetBorder.Style.DOUBLE) {
            canvas.drawLine(
                    startX + doubleInsetX,
                    startY + doubleInsetY,
                    endX + doubleInsetX,
                    endY + doubleInsetY,
                    borderPaint
            );
        }
    }

    private void drawGridRect(Canvas canvas, RectF bounds) {
        borderPaint.setColor(gridColor);
        borderPaint.setStrokeWidth(Math.max(1f, density));
        borderPaint.setPathEffect(null);
        canvas.drawRect(bounds, borderPaint);
    }

    private float borderWidth(SpreadsheetBorder.Style style) {
        if (style == SpreadsheetBorder.Style.HAIR) return Math.max(0.75f, density * 0.5f);
        if (style == SpreadsheetBorder.Style.MEDIUM
                || style == SpreadsheetBorder.Style.DOUBLE) {
            return clamp(1.5f * density, 1.5f, 3f * density);
        }
        if (style == SpreadsheetBorder.Style.THICK) {
            return clamp(2.5f * density, 2f, 4f * density);
        }
        return clamp(density, 1f, 2f * density);
    }

    private Layout.Alignment layoutAlignment(SpreadsheetCellStyle style, String value) {
        switch (style.getHorizontalAlignment()) {
            case CENTER:
                return Layout.Alignment.ALIGN_CENTER;
            case RIGHT:
                return Layout.Alignment.ALIGN_OPPOSITE;
            case GENERAL:
                return looksNumeric(value)
                        ? Layout.Alignment.ALIGN_OPPOSITE
                        : Layout.Alignment.ALIGN_NORMAL;
            case JUSTIFY:
            case LEFT:
            default:
                return Layout.Alignment.ALIGN_NORMAL;
        }
    }

    private float horizontalTextPosition(
            SpreadsheetCellStyle style,
            String value,
            float left,
            float right,
            float textWidth
    ) {
        SpreadsheetCellStyle.HorizontalAlignment alignment = style.getHorizontalAlignment();
        if (alignment == SpreadsheetCellStyle.HorizontalAlignment.CENTER) {
            return left + Math.max(0f, (right - left - textWidth) / 2f);
        }
        if (alignment == SpreadsheetCellStyle.HorizontalAlignment.RIGHT
                || (alignment == SpreadsheetCellStyle.HorizontalAlignment.GENERAL
                && looksNumeric(value))) {
            return right - textWidth;
        }
        return left;
    }

    private float verticalBaseline(
            float top,
            float bottom,
            Paint.FontMetrics metrics,
            SpreadsheetCellStyle.VerticalAlignment alignment
    ) {
        if (alignment == SpreadsheetCellStyle.VerticalAlignment.TOP) {
            return top - metrics.ascent;
        }
        if (alignment == SpreadsheetCellStyle.VerticalAlignment.CENTER) {
            return (top + bottom - metrics.ascent - metrics.descent) / 2f;
        }
        return bottom - metrics.descent;
    }

    private float verticalBlockTop(
            float top,
            float bottom,
            float blockHeight,
            SpreadsheetCellStyle.VerticalAlignment alignment
    ) {
        if (alignment == SpreadsheetCellStyle.VerticalAlignment.TOP) return top;
        if (alignment == SpreadsheetCellStyle.VerticalAlignment.CENTER) {
            return top + Math.max(0f, (bottom - top - blockHeight) / 2f);
        }
        return Math.max(top, bottom - blockHeight);
    }

    private boolean looksNumeric(String value) {
        if (value == null || value.isEmpty()) return false;
        int index = value.charAt(0) == '-' || value.charAt(0) == '+' ? 1 : 0;
        boolean digit = false;
        for (; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isDigit(current)) digit = true;
            else if (current != '.' && current != ',' && current != ' ') return false;
        }
        return digit;
    }

    private void setCellScreenBounds(SpreadsheetGeometry geometry, int row, int column) {
        setScreenBounds(
                geometry.getColumnLeft(column),
                geometry.getRowTop(row),
                geometry.getColumnRight(column),
                geometry.getRowBottom(row)
        );
    }

    private void setScreenBounds(float left, float top, float right, float bottom) {
        reusableRect.set(
                bodyScreenX(left),
                bodyScreenY(top),
                bodyScreenX(right),
                bodyScreenY(bottom)
        );
    }

    private float bodyScreenX(float sheetX) {
        return rowHeaderWidth + (sheetX - transform.getOffsetX()) * transform.getScale();
    }

    private float bodyScreenY(float sheetY) {
        return columnHeaderHeight + (sheetY - transform.getOffsetY()) * transform.getScale();
    }

    private boolean intersectsBody(RectF bounds) {
        return bounds.right > rowHeaderWidth
                && bounds.left < getWidth()
                && bounds.bottom > columnHeaderHeight
                && bounds.top < getHeight();
    }

    private float getContentWidth() {
        return Math.max(0f, getWidth() - rowHeaderWidth);
    }

    private float getContentHeight() {
        return Math.max(0f, getHeight() - columnHeaderHeight);
    }

    private void nextMergedDrawGeneration() {
        mergedDrawGeneration++;
        if (mergedDrawGeneration == Integer.MAX_VALUE) {
            java.util.Arrays.fill(mergedDrawStamps, 0);
            mergedDrawGeneration = 1;
        }
    }

    private boolean wasMergedDrawn(int rangeId) {
        return rangeId >= 0
                && rangeId < mergedDrawStamps.length
                && mergedDrawStamps[rangeId] == mergedDrawGeneration;
    }

    private void markMergedDrawn(int rangeId) {
        if (rangeId >= 0 && rangeId < mergedDrawStamps.length) {
            mergedDrawStamps[rangeId] = mergedDrawGeneration;
        }
    }

    private void viewportChanged() {
        accessibilityHelper.invalidateRoot();
        ViewCompat.postInvalidateOnAnimation(this);
    }

    private void startFling(float velocityX, float velocityY) {
        lastFlingX = 0;
        lastFlingY = 0;
        int minimumX = -Math.round(transform.getOffsetX() * transform.getScale());
        int maximumX = Math.round(
                (transform.getMaximumOffsetX() - transform.getOffsetX())
                        * transform.getScale()
        );
        int minimumY = -Math.round(transform.getOffsetY() * transform.getScale());
        int maximumY = Math.round(
                (transform.getMaximumOffsetY() - transform.getOffsetY())
                        * transform.getScale()
        );
        flingScroller.fling(
                0,
                0,
                Math.round(-velocityX),
                Math.round(-velocityY),
                minimumX,
                maximumX,
                minimumY,
                maximumY
        );
        ViewCompat.postInvalidateOnAnimation(this);
    }

    private void stopMotion() {
        if (!flingScroller.isFinished()) flingScroller.abortAnimation();
    }

    private void notifyZoomChanged(boolean finished, boolean userInitiated) {
        if (zoomChangeListener != null) {
            zoomChangeListener.onZoomChanged(
                    transform.getScale(),
                    zoomMode,
                    finished,
                    userInitiated
            );
        }
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private final class ScaleListener
            extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            stopMotion();
            scaling = true;
            gestureContainedScale = true;
            skipNextPan = true;
            targetScale = ZoomController.clampZoom(transform.getScale());
            zoomMode = ZoomController.ZoomMode.MANUAL;
            notifyZoomChanged(false, true);
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            targetScale = ZoomController.clampZoom(
                    targetScale * detector.getScaleFactor()
            );
            transform.zoomAround(
                    targetScale,
                    detector.getFocusX() - rowHeaderWidth,
                    detector.getFocusY() - columnHeaderHeight,
                    ZoomController.ZoomMode.MANUAL
            );
            zoomMode = ZoomController.ZoomMode.MANUAL;
            viewportChanged();
            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            scaling = false;
            skipNextPan = true;
            viewportChanged();
            notifyZoomChanged(true, true);
        }
    }

    private final class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDown(MotionEvent event) {
            return true;
        }

        @Override
        public boolean onScroll(
                MotionEvent first,
                MotionEvent current,
                float distanceX,
                float distanceY
        ) {
            if (scaling || scaleGestureDetector.isInProgress()) return true;
            if (skipNextPan) {
                skipNextPan = false;
                return true;
            }
            transform.panByScreen(distanceX, distanceY);
            viewportChanged();
            return true;
        }

        @Override
        public boolean onFling(
                MotionEvent first,
                MotionEvent second,
                float velocityX,
                float velocityY
        ) {
            if (gestureContainedScale || scaling || scaleGestureDetector.isInProgress()) {
                return false;
            }
            startFling(velocityX, velocityY);
            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent event) {
            if (zoomMode == ZoomController.ZoomMode.ZOOM_100) {
                fitToWidth();
            } else {
                transform.zoomAround(
                        ZoomController.NORMAL_ZOOM,
                        event.getX() - rowHeaderWidth,
                        event.getY() - columnHeaderHeight,
                        ZoomController.ZoomMode.ZOOM_100
                );
                zoomMode = ZoomController.ZoomMode.ZOOM_100;
                targetScale = ZoomController.NORMAL_ZOOM;
                viewportChanged();
                notifyZoomChanged(true, true);
            }
            return true;
        }
    }

    interface OnZoomChangeListener {
        void onZoomChanged(
                float scale,
                ZoomController.ZoomMode zoomMode,
                boolean finished,
                boolean userInitiated
        );
    }
}
