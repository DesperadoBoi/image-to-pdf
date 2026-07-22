package com.desperadoboi.imagetopdf.ui.viewer;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.OverScroller;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.NestedScrollingChild3;
import androidx.core.view.NestedScrollingChildHelper;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.desperadoboi.imagetopdf.R;
import com.desperadoboi.imagetopdf.document.spreadsheet.ColumnLabelFormatter;
import com.desperadoboi.imagetopdf.document.spreadsheet.SpreadsheetData;

public final class SpreadsheetViewport extends ViewGroup implements NestedScrollingChild3 {
    private final SpreadsheetRowAdapter rowAdapter = new SpreadsheetRowAdapter();
    private final RecyclerView bodyRecycler;
    private final LinearLayoutManager bodyLayoutManager;
    private final FrameLayout headerContainer;
    private final LinearLayout columnHeaders;
    private final TextView cornerHeader;
    private final ScaleGestureDetector scaleGestureDetector;
    private final GestureDetector gestureDetector;
    private final OverScroller flingScroller;
    private final NestedScrollingChildHelper nestedScrollingChildHelper;
    private final SpreadsheetAxisOffsets synchronizedOffsets = new SpreadsheetAxisOffsets();
    private final int[] nestedConsumed = new int[2];
    private final int[] nestedOffset = new int[2];
    private final Runnable scaleFrame = this::applyPendingScale;

    private float horizontalOffset;
    private float targetScale = ZoomController.NORMAL_ZOOM;
    private float pendingScale = Float.NaN;
    private float pendingFocusX;
    private float pendingFocusY;
    private boolean scaleFramePosted;
    private boolean scaling;
    private boolean pinchInGesture;
    private boolean skipNextPan;
    private ZoomController.ZoomMode zoomMode = ZoomController.ZoomMode.ZOOM_100;
    private boolean hasData;
    @Nullable private SpreadsheetViewportState pendingState;
    @Nullable private OnZoomChangeListener zoomChangeListener;

    public SpreadsheetViewport(Context context) {
        this(context, null);
    }

    public SpreadsheetViewport(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SpreadsheetViewport(
            Context context,
            @Nullable AttributeSet attrs,
            int defStyleAttr
    ) {
        super(context, attrs, defStyleAttr);
        setClipChildren(true);
        setClickable(true);
        setFocusable(true);

        bodyLayoutManager = new LinearLayoutManager(context);
        bodyRecycler = new RecyclerView(context);
        bodyRecycler.setLayoutManager(bodyLayoutManager);
        bodyRecycler.setAdapter(rowAdapter);
        bodyRecycler.setItemAnimator(null);
        bodyRecycler.setNestedScrollingEnabled(false);
        bodyRecycler.setVerticalScrollBarEnabled(true);
        bodyRecycler.setOverScrollMode(OVER_SCROLL_NEVER);
        addView(bodyRecycler);

        headerContainer = new FrameLayout(context);
        headerContainer.setClipChildren(true);
        columnHeaders = new LinearLayout(context);
        columnHeaders.setOrientation(LinearLayout.HORIZONTAL);
        headerContainer.addView(columnHeaders);
        cornerHeader = createHeaderCell(true);
        cornerHeader.setText("");
        headerContainer.addView(cornerHeader);
        addView(headerContainer);

        scaleGestureDetector = new ScaleGestureDetector(context, new ScaleListener());
        gestureDetector = new GestureDetector(context, new GestureListener());
        flingScroller = new OverScroller(context);
        nestedScrollingChildHelper = new NestedScrollingChildHelper(this);
        setNestedScrollingEnabled(true);
    }

    void submit(SpreadsheetData data, SpreadsheetViewportState state) {
        stopMotion();
        horizontalOffset = 0f;
        rowAdapter.setHorizontalOffset(0f);
        bodyLayoutManager.scrollToPositionWithOffset(0, 0);
        zoomMode = state.getZoomMode();
        rowAdapter.setZoom(state.getScale(), zoomMode);
        rowAdapter.submit(data, this);
        hasData = !data.getRows().isEmpty();
        rebuildColumnHeaders();
        pendingState = state;
        requestLayout();
        post(this::restorePendingState);
    }

    SpreadsheetViewportState captureState() {
        float scale = rowAdapter.getScale();
        float verticalOffset = getVerticalOffset();
        float contentWidth = getContentViewportWidth();
        float bodyHeight = bodyRecycler.getHeight();
        float centerX = (horizontalOffset + (contentWidth / 2f)) / scale;
        float centerY = (verticalOffset + (bodyHeight / 2f))
                / rowAdapter.getVerticalScale();
        return SpreadsheetViewportState.positioned(
                scale,
                horizontalOffset,
                verticalOffset,
                centerX,
                centerY,
                zoomMode
        );
    }

    void fitToWidth() {
        if (!hasData || getWidth() <= 0) return;
        stopMotion();
        float nextScale = calculateFitScale();
        applyScaleImmediately(
                nextScale,
                getWidth() / 2f,
                getHeight() / 2f,
                ZoomController.ZoomMode.FIT_WIDTH
        );
        setHorizontalOffset(0f);
        notifyZoomChanged(true, true);
    }

    void zoomToNormal() {
        if (!hasData || getWidth() <= 0) return;
        stopMotion();
        applyScaleImmediately(
                ZoomController.NORMAL_ZOOM,
                getWidth() / 2f,
                getHeight() / 2f,
                ZoomController.ZoomMode.ZOOM_100
        );
        notifyZoomChanged(true, true);
    }

    ZoomController.ZoomMode getZoomMode() {
        return zoomMode;
    }

    float getZoom() {
        return rowAdapter.getScale();
    }

    void setOnZoomChangeListener(@Nullable OnZoomChangeListener listener) {
        zoomChangeListener = listener;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec);
        int height = resolveSize(getSuggestedMinimumHeight(), heightMeasureSpec);
        setMeasuredDimension(width, height);

        int headerHeight = rowAdapter.getHeaderHeight();
        int bodyHeight = Math.max(0, height - headerHeight);
        headerContainer.measure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(headerHeight, MeasureSpec.EXACTLY)
        );
        bodyRecycler.measure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(bodyHeight, MeasureSpec.EXACTLY)
        );
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int width = right - left;
        int height = bottom - top;
        int headerHeight = rowAdapter.getHeaderHeight();
        headerContainer.layout(0, 0, width, headerHeight);
        bodyRecycler.layout(0, headerHeight, width, height);
        if (pendingState != null) post(this::restorePendingState);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (!hasData || oldWidth <= 0 || oldHeight <= 0 || pendingState != null) return;
        float scale = rowAdapter.getScale();
        float oldContentWidth = Math.max(0f, oldWidth - rowAdapter.getRowHeaderWidth());
        float oldBodyHeight = Math.max(0f, oldHeight - rowAdapter.getHeaderHeight());
        SpreadsheetViewportState state = SpreadsheetViewportState.positioned(
                scale,
                horizontalOffset,
                getVerticalOffset(),
                (horizontalOffset + (oldContentWidth / 2f)) / scale,
                (getVerticalOffset() + (oldBodyHeight / 2f))
                        / rowAdapter.getVerticalScale(),
                zoomMode
        );
        pendingState = state;
        post(this::restorePendingState);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        return hasData;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!hasData) return super.onTouchEvent(event);
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            stopMotion();
            pinchInGesture = false;
            skipNextPan = false;
            startNestedScroll(
                    ViewCompat.SCROLL_AXIS_HORIZONTAL | ViewCompat.SCROLL_AXIS_VERTICAL,
                    ViewCompat.TYPE_TOUCH
            );
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
        }

        scaleGestureDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            stopNestedScroll(ViewCompat.TYPE_TOUCH);
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
            pinchInGesture = false;
            skipNextPan = false;
            if (action == MotionEvent.ACTION_CANCEL) {
                pendingScale = Float.NaN;
                scaling = false;
            }
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
        if (!flingScroller.computeScrollOffset()) {
            stopNestedScroll(ViewCompat.TYPE_NON_TOUCH);
            return;
        }
        float distanceX = flingScroller.getCurrX() - horizontalOffset;
        float distanceY = flingScroller.getCurrY() - getVerticalOffset();
        panBy(distanceX, distanceY, ViewCompat.TYPE_NON_TOUCH);
        ViewCompat.postInvalidateOnAnimation(this);
    }

    private void restorePendingState() {
        SpreadsheetViewportState state = pendingState;
        if (state == null || getWidth() <= 0 || bodyRecycler.getHeight() <= 0) return;
        pendingState = null;
        zoomMode = state.getZoomMode();
        float scale = zoomMode == ZoomController.ZoomMode.FIT_WIDTH
                ? calculateFitScale()
                : state.getScale();
        rowAdapter.setZoom(scale, zoomMode);
        targetScale = scale;
        updateHeaderMetrics();

        float horizontal = state.getHorizontalOffset();
        float vertical = state.getVerticalOffset();
        if (state.hasViewportPosition()) {
            horizontal = (state.getCenterContentX() * scale)
                    - (getContentViewportWidth() / 2f);
            vertical = (state.getCenterContentY() * rowAdapter.getVerticalScale())
                    - (bodyRecycler.getHeight() / 2f);
        }
        setHorizontalOffset(horizontal);
        jumpToVerticalOffset(vertical);
    }

    private float calculateFitScale() {
        return ZoomController.calculateFitScale(
                getWidth(),
                rowAdapter.getRowHeaderWidth(),
                rowAdapter.getUnscaledSheetWidth()
        );
    }

    private void rebuildColumnHeaders() {
        columnHeaders.removeAllViews();
        int columnCount = rowAdapter.getScaledColumnWidths().length;
        for (int column = 0; column < columnCount; column++) {
            TextView header = createHeaderCell(false);
            String label = ColumnLabelFormatter.format(column);
            header.setText(label);
            header.setContentDescription(getResources().getString(
                    R.string.viewer_column_header_content_description,
                    label
            ));
            columnHeaders.addView(header);
        }
        updateHeaderMetrics();
    }

    private TextView createHeaderCell(boolean corner) {
        TextView header = new TextView(getContext());
        header.setGravity(Gravity.CENTER);
        header.setMaxLines(1);
        header.setTextColor(ContextCompat.getColor(getContext(), R.color.viewer_document_text));
        header.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        header.setBackgroundResource(corner
                ? R.drawable.bg_viewer_grid_corner_header
                : R.drawable.bg_viewer_grid_header);
        return header;
    }

    private void updateHeaderMetrics() {
        int rowHeaderWidth = rowAdapter.getRowHeaderWidth();
        int headerHeight = rowAdapter.getHeaderHeight();
        int padding = rowAdapter.getScaledPadding();
        float textSize = rowAdapter.getScaledTextSize();

        FrameLayout.LayoutParams columnsParams = new FrameLayout.LayoutParams(
                rowAdapter.getScaledSheetWidth(),
                headerHeight
        );
        columnsParams.leftMargin = rowHeaderWidth;
        columnHeaders.setLayoutParams(columnsParams);
        int[] widths = rowAdapter.getScaledColumnWidths();
        for (int column = 0; column < widths.length; column++) {
            TextView header = (TextView) columnHeaders.getChildAt(column);
            header.setLayoutParams(new LinearLayout.LayoutParams(
                    widths[column],
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
            header.setPadding(padding, 0, padding, 0);
            header.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
        }

        cornerHeader.setLayoutParams(new FrameLayout.LayoutParams(rowHeaderWidth, headerHeight));
        cornerHeader.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
        columnHeaders.setTranslationX(-horizontalOffset);
        headerContainer.requestLayout();
    }

    private void queueScale(float scale, float focusX, float focusY) {
        pendingScale = ZoomController.clampZoom(scale);
        pendingFocusX = focusX;
        pendingFocusY = focusY;
        if (scaleFramePosted) return;
        scaleFramePosted = true;
        ViewCompat.postOnAnimation(this, scaleFrame);
    }

    private void applyPendingScale() {
        scaleFramePosted = false;
        if (Float.isNaN(pendingScale)) return;
        float scale = pendingScale;
        pendingScale = Float.NaN;
        applyScaleImmediately(
                scale,
                pendingFocusX,
                pendingFocusY,
                ZoomController.ZoomMode.MANUAL
        );
        notifyZoomChanged(false, true);
    }

    private void applyScaleImmediately(
            float scale,
            float focusX,
            float focusY,
            ZoomController.ZoomMode nextZoomMode
    ) {
        float oldScale = rowAdapter.getScale();
        float oldVerticalScale = rowAdapter.getVerticalScale();
        float newScale = ZoomController.clampZoom(scale, nextZoomMode);
        boolean scaleChanged = Math.abs(oldScale - newScale) >= 0.0001f;
        boolean modeChanged = zoomMode != nextZoomMode;
        if (!scaleChanged && !modeChanged) return;

        float localFocusX = clamp(
                focusX - rowAdapter.getRowHeaderWidth(),
                0f,
                getContentViewportWidth()
        );
        float localFocusY = clamp(
                focusY - rowAdapter.getHeaderHeight(),
                0f,
                bodyRecycler.getHeight()
        );
        float nextHorizontalOffset = ZoomController.preserveFocalPoint(
                horizontalOffset,
                oldScale,
                newScale,
                localFocusX
        );
        zoomMode = nextZoomMode;
        rowAdapter.setZoom(newScale, zoomMode);
        float nextVerticalOffset = ZoomController.preserveFocalPoint(
                getVerticalOffset(),
                oldVerticalScale,
                rowAdapter.getVerticalScale(),
                localFocusY
        );
        targetScale = newScale;
        updateHeaderMetrics();
        requestLayout();
        setHorizontalOffset(nextHorizontalOffset);
        jumpToVerticalOffset(nextVerticalOffset);
    }

    private void panBy(float distanceX, float distanceY, int type) {
        int requestedX = Math.round(distanceX);
        int requestedY = Math.round(distanceY);
        nestedConsumed[0] = 0;
        nestedConsumed[1] = 0;
        dispatchNestedPreScroll(
                requestedX,
                requestedY,
                nestedConsumed,
                nestedOffset,
                type
        );

        float oldHorizontal = horizontalOffset;
        float oldVertical = getVerticalOffset();
        setHorizontalOffset(horizontalOffset + requestedX - nestedConsumed[0]);
        setVerticalOffset(oldVertical + requestedY - nestedConsumed[1]);
        int consumedX = Math.round(horizontalOffset - oldHorizontal);
        int consumedY = Math.round(getVerticalOffset() - oldVertical);
        int unconsumedX = requestedX - nestedConsumed[0] - consumedX;
        int unconsumedY = requestedY - nestedConsumed[1] - consumedY;
        nestedConsumed[0] = 0;
        nestedConsumed[1] = 0;
        dispatchNestedScroll(
                consumedX,
                consumedY,
                unconsumedX,
                unconsumedY,
                nestedOffset,
                type,
                nestedConsumed
        );
    }

    private void setHorizontalOffset(float offset) {
        horizontalOffset = clamp(offset, 0f, getMaximumHorizontalOffset());
        synchronizedOffsets.setHorizontal(horizontalOffset);
        rowAdapter.setHorizontalOffset(synchronizedOffsets.getBodyHorizontal());
        columnHeaders.setTranslationX(-synchronizedOffsets.getColumnHeaderHorizontal());
    }

    private void setVerticalOffset(float offset) {
        int clampedOffset = Math.round(clamp(offset, 0f, getMaximumVerticalOffset()));
        int previousOffset = Math.round(synchronizedOffsets.getBodyVertical());
        bodyRecycler.scrollBy(0, clampedOffset - previousOffset);
        synchronizedOffsets.setVertical(clampedOffset);
    }

    private void jumpToVerticalOffset(float offset) {
        int rowHeight = rowAdapter.getScaledRowHeight();
        int clampedOffset = Math.round(clamp(offset, 0f, getMaximumVerticalOffset()));
        int position = rowHeight <= 0 ? 0 : clampedOffset / rowHeight;
        int remainder = rowHeight <= 0 ? 0 : clampedOffset % rowHeight;
        if (rowAdapter.getItemCount() == 0) return;
        position = Math.min(position, rowAdapter.getItemCount() - 1);
        bodyLayoutManager.scrollToPositionWithOffset(position, -remainder);
        synchronizedOffsets.setVertical(clampedOffset);
    }

    private float getVerticalOffset() {
        return synchronizedOffsets.getBodyVertical();
    }

    private float getMaximumHorizontalOffset() {
        return Math.max(0f, rowAdapter.getScaledSheetWidth() - getContentViewportWidth());
    }

    private float getMaximumVerticalOffset() {
        return Math.max(
                0f,
                (rowAdapter.getItemCount() * (float) rowAdapter.getScaledRowHeight())
                        - bodyRecycler.getHeight()
        );
    }

    private float getContentViewportWidth() {
        return Math.max(0f, getWidth() - rowAdapter.getRowHeaderWidth());
    }

    private void startFling(float velocityX, float velocityY) {
        if (dispatchNestedPreFling(-velocityX, -velocityY)) return;
        dispatchNestedFling(-velocityX, -velocityY, true);
        startNestedScroll(
                ViewCompat.SCROLL_AXIS_HORIZONTAL | ViewCompat.SCROLL_AXIS_VERTICAL,
                ViewCompat.TYPE_NON_TOUCH
        );
        flingScroller.fling(
                Math.round(horizontalOffset),
                Math.round(getVerticalOffset()),
                Math.round(-velocityX),
                Math.round(-velocityY),
                0,
                Math.round(getMaximumHorizontalOffset()),
                0,
                Math.round(getMaximumVerticalOffset())
        );
        ViewCompat.postInvalidateOnAnimation(this);
    }

    private void stopMotion() {
        flingScroller.abortAnimation();
        stopNestedScroll(ViewCompat.TYPE_NON_TOUCH);
        bodyRecycler.stopScroll();
    }

    private void notifyZoomChanged(boolean finished, boolean userInitiated) {
        if (zoomChangeListener != null) {
            zoomChangeListener.onZoomChanged(
                    rowAdapter.getScale(),
                    zoomMode,
                    finished,
                    userInitiated
            );
        }
    }

    private float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private final class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            stopMotion();
            scaling = true;
            pinchInGesture = true;
            skipNextPan = true;
            targetScale = ZoomController.clampZoom(rowAdapter.getScale());
            applyScaleImmediately(
                    targetScale,
                    detector.getFocusX(),
                    detector.getFocusY(),
                    ZoomController.ZoomMode.MANUAL
            );
            notifyZoomChanged(false, true);
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            targetScale = ZoomController.clampZoom(
                    targetScale * detector.getScaleFactor()
            );
            queueScale(targetScale, detector.getFocusX(), detector.getFocusY());
            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            applyPendingScale();
            scaling = false;
            skipNextPan = true;
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
            panBy(distanceX, distanceY, ViewCompat.TYPE_TOUCH);
            return true;
        }

        @Override
        public boolean onFling(
                MotionEvent first,
                MotionEvent second,
                float velocityX,
                float velocityY
        ) {
            if (pinchInGesture || scaling || scaleGestureDetector.isInProgress()) return false;
            startFling(velocityX, velocityY);
            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent event) {
            stopMotion();
            if (zoomMode == ZoomController.ZoomMode.ZOOM_100) {
                applyScaleImmediately(
                        calculateFitScale(),
                        event.getX(),
                        event.getY(),
                        ZoomController.ZoomMode.FIT_WIDTH
                );
                setHorizontalOffset(0f);
            } else {
                applyScaleImmediately(
                        ZoomController.NORMAL_ZOOM,
                        event.getX(),
                        event.getY(),
                        ZoomController.ZoomMode.ZOOM_100
                );
            }
            notifyZoomChanged(true, true);
            return true;
        }
    }

    @Override
    public void setNestedScrollingEnabled(boolean enabled) {
        nestedScrollingChildHelper.setNestedScrollingEnabled(enabled);
    }

    @Override
    public boolean isNestedScrollingEnabled() {
        return nestedScrollingChildHelper.isNestedScrollingEnabled();
    }

    @Override
    public boolean startNestedScroll(int axes) {
        return nestedScrollingChildHelper.startNestedScroll(axes);
    }

    @Override
    public boolean startNestedScroll(int axes, int type) {
        return nestedScrollingChildHelper.startNestedScroll(axes, type);
    }

    @Override
    public void stopNestedScroll() {
        nestedScrollingChildHelper.stopNestedScroll();
    }

    @Override
    public void stopNestedScroll(int type) {
        nestedScrollingChildHelper.stopNestedScroll(type);
    }

    @Override
    public boolean hasNestedScrollingParent() {
        return nestedScrollingChildHelper.hasNestedScrollingParent();
    }

    @Override
    public boolean hasNestedScrollingParent(int type) {
        return nestedScrollingChildHelper.hasNestedScrollingParent(type);
    }

    @Override
    public boolean dispatchNestedScroll(
            int consumedX,
            int consumedY,
            int unconsumedX,
            int unconsumedY,
            @Nullable int[] offsetInWindow
    ) {
        return nestedScrollingChildHelper.dispatchNestedScroll(
                consumedX,
                consumedY,
                unconsumedX,
                unconsumedY,
                offsetInWindow
        );
    }

    @Override
    public boolean dispatchNestedScroll(
            int consumedX,
            int consumedY,
            int unconsumedX,
            int unconsumedY,
            @Nullable int[] offsetInWindow,
            int type
    ) {
        return nestedScrollingChildHelper.dispatchNestedScroll(
                consumedX,
                consumedY,
                unconsumedX,
                unconsumedY,
                offsetInWindow,
                type
        );
    }

    @Override
    public void dispatchNestedScroll(
            int consumedX,
            int consumedY,
            int unconsumedX,
            int unconsumedY,
            @Nullable int[] offsetInWindow,
            int type,
            @NonNull int[] consumed
    ) {
        nestedScrollingChildHelper.dispatchNestedScroll(
                consumedX,
                consumedY,
                unconsumedX,
                unconsumedY,
                offsetInWindow,
                type,
                consumed
        );
    }

    @Override
    public boolean dispatchNestedPreScroll(
            int dx,
            int dy,
            @Nullable int[] consumed,
            @Nullable int[] offsetInWindow
    ) {
        return nestedScrollingChildHelper.dispatchNestedPreScroll(
                dx,
                dy,
                consumed,
                offsetInWindow
        );
    }

    @Override
    public boolean dispatchNestedPreScroll(
            int dx,
            int dy,
            @Nullable int[] consumed,
            @Nullable int[] offsetInWindow,
            int type
    ) {
        return nestedScrollingChildHelper.dispatchNestedPreScroll(
                dx,
                dy,
                consumed,
                offsetInWindow,
                type
        );
    }

    @Override
    public boolean dispatchNestedFling(float velocityX, float velocityY, boolean consumed) {
        return nestedScrollingChildHelper.dispatchNestedFling(
                velocityX,
                velocityY,
                consumed
        );
    }

    @Override
    public boolean dispatchNestedPreFling(float velocityX, float velocityY) {
        return nestedScrollingChildHelper.dispatchNestedPreFling(velocityX, velocityY);
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
