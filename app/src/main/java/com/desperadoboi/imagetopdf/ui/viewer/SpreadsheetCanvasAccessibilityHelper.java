package com.desperadoboi.imagetopdf.ui.viewer;

import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;

import com.desperadoboi.imagetopdf.R;
import com.desperadoboi.imagetopdf.document.spreadsheet.SpreadsheetMergedRange;

import java.util.Collections;
import java.util.List;

final class SpreadsheetCanvasAccessibilityHelper extends AccessibilityNodeProvider {
    static final int ACTION_ZOOM_100 = R.id.accessibility_action_spreadsheet_zoom_100;
    static final int ACTION_FIT_WIDTH = R.id.accessibility_action_spreadsheet_fit_width;
    static final int ACTION_FIT_SHEET = R.id.accessibility_action_spreadsheet_fit_sheet;
    static final int ACTION_SCROLL_LEFT =
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT.getId();
    static final int ACTION_SCROLL_RIGHT =
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.getId();
    static final int ACTION_SCROLL_UP =
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.getId();
    static final int ACTION_SCROLL_DOWN =
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.getId();

    private static final int HOST_ID = View.NO_ID;
    private static final int ROW_HEADER_ID_BASE = 1_000_000;
    private static final int COLUMN_HEADER_ID_BASE = 2_000_000;
    private static final long CONTENT_CHANGE_DELAY_MILLIS = 80L;

    private final SpreadsheetCanvasView view;
    private final AccessibilityManager accessibilityManager;
    private final Rect reusableBounds = new Rect();
    private final Runnable sendRootChangedAction = this::sendRootChanged;
    private List<Integer> visibleCellIds = Collections.emptyList();
    private List<Integer> visibleRowIds = Collections.emptyList();
    private List<Integer> visibleColumnIds = Collections.emptyList();
    private boolean visibleNodesDirty = true;
    private int accessibilityFocusedId = HOST_ID;
    private int hoveredId = HOST_ID;

    SpreadsheetCanvasAccessibilityHelper(SpreadsheetCanvasView view) {
        this.view = view;
        accessibilityManager = (AccessibilityManager) view.getContext()
                .getSystemService(Context.ACCESSIBILITY_SERVICE);
    }

    @Override
    public AccessibilityNodeInfo createAccessibilityNodeInfo(int virtualViewId) {
        if (virtualViewId == HOST_ID) return createHostNode();
        if (virtualViewId >= COLUMN_HEADER_ID_BASE) {
            return createColumnHeaderNode(virtualViewId - COLUMN_HEADER_ID_BASE);
        }
        if (virtualViewId >= ROW_HEADER_ID_BASE) {
            return createRowHeaderNode(virtualViewId - ROW_HEADER_ID_BASE);
        }
        return createCellNode(virtualViewId);
    }

    @Override
    public boolean performAction(
            int virtualViewId,
            int action,
        Bundle arguments
    ) {
        if (virtualViewId == HOST_ID) {
            return view.performAccessibilityViewportAction(action)
                    || view.performAccessibilityAction(action, arguments);
        }
        if (!isVisibleVirtualId(virtualViewId)) return false;
        if (action == AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS) {
            if (accessibilityFocusedId == virtualViewId) return false;
            int previous = accessibilityFocusedId;
            accessibilityFocusedId = virtualViewId;
            if (previous != HOST_ID) {
                sendEvent(previous, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED);
            }
            sendEvent(virtualViewId, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);
            view.invalidate();
            return true;
        }
        if (action == AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS) {
            if (accessibilityFocusedId != virtualViewId) return false;
            accessibilityFocusedId = HOST_ID;
            sendEvent(
                    virtualViewId,
                    AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED
            );
            view.invalidate();
            return true;
        }
        return false;
    }

    @Override
    public AccessibilityNodeInfo findFocus(int focus) {
        if (focus != AccessibilityNodeInfo.FOCUS_ACCESSIBILITY
                || accessibilityFocusedId == HOST_ID) {
            return null;
        }
        return createAccessibilityNodeInfo(accessibilityFocusedId);
    }

    boolean dispatchHoverEvent(MotionEvent event) {
        if (accessibilityManager == null
                || !accessibilityManager.isEnabled()
                || !accessibilityManager.isTouchExplorationEnabled()) {
            return false;
        }
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_HOVER_EXIT) {
            updateHoveredId(HOST_ID);
            return true;
        }
        if (action != MotionEvent.ACTION_HOVER_ENTER
                && action != MotionEvent.ACTION_HOVER_MOVE) {
            return false;
        }
        int virtualId = hitTest(event.getX(), event.getY());
        updateHoveredId(virtualId);
        return virtualId != HOST_ID;
    }

    void invalidateRoot() {
        visibleNodesDirty = true;
        if (accessibilityManager == null || !accessibilityManager.isEnabled()) return;
        view.removeCallbacks(sendRootChangedAction);
        view.postDelayed(sendRootChangedAction, CONTENT_CHANGE_DELAY_MILLIS);
    }

    private AccessibilityNodeInfo createHostNode() {
        AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain(view);
        view.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(SpreadsheetCanvasView.class.getName());
        info.setPackageName(view.getContext().getPackageName());
        info.setSource(view);
        info.setScrollable(true);
        addViewportActions(info);

        SpreadsheetCanvasModel model = view.getCanvasModel();
        if (model == null) return info;
        ensureVisibleNodes(model);
        info.setCollectionInfo(AccessibilityNodeInfo.CollectionInfo.obtain(
                model.getGeometry().getRowCount(),
                model.getGeometry().getColumnCount(),
                false
        ));
        for (int cellId : visibleCellIds) {
            info.addChild(view, cellId);
        }
        for (int row : visibleRowIds) {
            info.addChild(view, ROW_HEADER_ID_BASE + row);
        }
        for (int column : visibleColumnIds) {
            info.addChild(view, COLUMN_HEADER_ID_BASE + column);
        }
        return info;
    }

    private AccessibilityNodeInfo createCellNode(int virtualId) {
        SpreadsheetCanvasModel model = view.getCanvasModel();
        if (model == null || !isVisibleCellId(model, virtualId)) return null;
        int columns = model.getGeometry().getColumnCount();
        int row = SpreadsheetAccessibilityModel.rowFromCellId(virtualId, columns);
        int column = SpreadsheetAccessibilityModel.columnFromCellId(virtualId, columns);
        String value = model.getValue(row, column);
        if (value.isEmpty()) return null;

        AccessibilityNodeInfo info = newVirtualNode(virtualId, "android.widget.TextView");
        SpreadsheetMergedRange merged = model.getMergedCellIndex().getRangeAt(row, column);
        if (merged == null) {
            info.setContentDescription(view.getResources().getString(
                    R.string.viewer_cell_content_description,
                    row + 1,
                    model.getColumnLabel(column),
                    value
            ));
            info.setCollectionItemInfo(AccessibilityNodeInfo.CollectionItemInfo.obtain(
                    row,
                    1,
                    column,
                    1,
                    false
            ));
        } else {
            info.setContentDescription(view.getResources().getString(
                    R.string.viewer_merged_cell_content_description,
                    merged.getFirstRow() + 1,
                    model.getColumnLabel(merged.getFirstColumn()),
                    merged.getLastRow() + 1,
                    model.getColumnLabel(merged.getLastColumn()),
                    value
            ));
            info.setCollectionItemInfo(AccessibilityNodeInfo.CollectionItemInfo.obtain(
                    merged.getFirstRow(),
                    merged.getLastRow() - merged.getFirstRow() + 1,
                    merged.getFirstColumn(),
                    merged.getLastColumn() - merged.getFirstColumn() + 1,
                    false
            ));
        }
        info.setText(value);
        view.getCellBoundsOnScreen(row, column, reusableBounds);
        finishVirtualNode(info, reusableBounds, virtualId);
        return info;
    }

    private AccessibilityNodeInfo createRowHeaderNode(int row) {
        SpreadsheetCanvasModel model = view.getCanvasModel();
        if (model == null) return null;
        ensureVisibleNodes(model);
        if (!visibleRowIds.contains(row)) return null;
        int virtualId = ROW_HEADER_ID_BASE + row;
        AccessibilityNodeInfo info = newVirtualNode(virtualId, "android.widget.TextView");
        info.setText(model.getRowLabel(row));
        info.setContentDescription(view.getResources().getString(
                R.string.viewer_row_header_content_description,
                row + 1
        ));
        view.getRowHeaderBoundsOnScreen(row, reusableBounds);
        finishVirtualNode(info, reusableBounds, virtualId);
        return info;
    }

    private AccessibilityNodeInfo createColumnHeaderNode(int column) {
        SpreadsheetCanvasModel model = view.getCanvasModel();
        if (model == null) return null;
        ensureVisibleNodes(model);
        if (!visibleColumnIds.contains(column)) return null;
        int virtualId = COLUMN_HEADER_ID_BASE + column;
        AccessibilityNodeInfo info = newVirtualNode(virtualId, "android.widget.TextView");
        info.setText(model.getColumnLabel(column));
        info.setContentDescription(view.getResources().getString(
                R.string.viewer_column_header_content_description,
                model.getColumnLabel(column)
        ));
        view.getColumnHeaderBoundsOnScreen(column, reusableBounds);
        finishVirtualNode(info, reusableBounds, virtualId);
        return info;
    }

    private AccessibilityNodeInfo newVirtualNode(int virtualId, String className) {
        AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain();
        info.setPackageName(view.getContext().getPackageName());
        info.setClassName(className);
        info.setSource(view, virtualId);
        info.setParent(view);
        info.setEnabled(view.isEnabled());
        info.setFocusable(true);
        return info;
    }

    private void finishVirtualNode(
            AccessibilityNodeInfo info,
            Rect bounds,
            int virtualId
    ) {
        info.setBoundsInParent(bounds);
        info.setVisibleToUser(!bounds.isEmpty() && view.isShown());
        if (accessibilityFocusedId == virtualId) {
            info.setAccessibilityFocused(true);
            info.addAction(AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS);
        } else {
            info.setAccessibilityFocused(false);
            info.addAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
        }
    }

    private void addViewportActions(AccessibilityNodeInfo info) {
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT);
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT);
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP);
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN);
        info.addAction(new AccessibilityNodeInfo.AccessibilityAction(
                ACTION_ZOOM_100,
                view.getResources().getString(R.string.viewer_zoom_100)
        ));
        info.addAction(new AccessibilityNodeInfo.AccessibilityAction(
                ACTION_FIT_WIDTH,
                view.getResources().getString(R.string.viewer_fit_width)
        ));
        info.addAction(new AccessibilityNodeInfo.AccessibilityAction(
                ACTION_FIT_SHEET,
                view.getResources().getString(R.string.viewer_fit_sheet)
        ));
    }

    private int hitTest(float x, float y) {
        int cellId = view.hitTestCell(x, y);
        if (cellId >= 0 && isVisibleVirtualId(cellId)) return cellId;
        int row = view.hitTestRowHeader(x, y);
        if (row >= 0) return ROW_HEADER_ID_BASE + row;
        int column = view.hitTestColumnHeader(x, y);
        if (column >= 0) return COLUMN_HEADER_ID_BASE + column;
        return HOST_ID;
    }

    private boolean isVisibleVirtualId(int virtualId) {
        SpreadsheetCanvasModel model = view.getCanvasModel();
        if (model == null) return false;
        ensureVisibleNodes(model);
        if (virtualId >= COLUMN_HEADER_ID_BASE) {
            return visibleColumnIds.contains(virtualId - COLUMN_HEADER_ID_BASE);
        }
        if (virtualId >= ROW_HEADER_ID_BASE) {
            return visibleRowIds.contains(virtualId - ROW_HEADER_ID_BASE);
        }
        return visibleCellIds.contains(virtualId);
    }

    private boolean isVisibleCellId(SpreadsheetCanvasModel model, int virtualId) {
        ensureVisibleNodes(model);
        return visibleCellIds.contains(virtualId);
    }

    private void ensureVisibleNodes(SpreadsheetCanvasModel model) {
        if (!visibleNodesDirty) return;
        SpreadsheetRenderPlan plan = view.getAccessibilityRenderPlan();
        visibleCellIds = SpreadsheetAccessibilityModel.visibleCellIds(model, plan);
        visibleRowIds = SpreadsheetAccessibilityModel.visibleRows(
                model.getGeometry(),
                plan
        );
        visibleColumnIds = SpreadsheetAccessibilityModel.visibleColumns(
                model.getGeometry(),
                plan
        );
        if (accessibilityFocusedId != HOST_ID
                && !visibleCellIds.contains(accessibilityFocusedId)
                && (accessibilityFocusedId < ROW_HEADER_ID_BASE
                || accessibilityFocusedId >= COLUMN_HEADER_ID_BASE
                || !visibleRowIds.contains(accessibilityFocusedId - ROW_HEADER_ID_BASE))
                && (accessibilityFocusedId < COLUMN_HEADER_ID_BASE
                || !visibleColumnIds.contains(
                        accessibilityFocusedId - COLUMN_HEADER_ID_BASE
                ))) {
            accessibilityFocusedId = HOST_ID;
        }
        visibleNodesDirty = false;
    }

    private void updateHoveredId(int nextId) {
        if (hoveredId == nextId) return;
        int previous = hoveredId;
        hoveredId = nextId;
        if (previous != HOST_ID) sendEvent(previous, AccessibilityEvent.TYPE_VIEW_HOVER_EXIT);
        if (nextId != HOST_ID) sendEvent(nextId, AccessibilityEvent.TYPE_VIEW_HOVER_ENTER);
    }

    private void sendRootChanged() {
        AccessibilityEvent event = AccessibilityEvent.obtain(
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        );
        event.setContentChangeTypes(AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE);
        event.setSource(view);
        sendEventThroughParent(event);
    }

    private void sendEvent(int virtualId, int eventType) {
        if (accessibilityManager == null || !accessibilityManager.isEnabled()) return;
        AccessibilityNodeInfo node = createAccessibilityNodeInfo(virtualId);
        if (node == null) return;
        AccessibilityEvent event = AccessibilityEvent.obtain(eventType);
        event.setPackageName(view.getContext().getPackageName());
        event.setClassName(node.getClassName());
        event.setContentDescription(node.getContentDescription());
        event.setSource(view, virtualId);
        sendEventThroughParent(event);
        node.recycle();
    }

    private void sendEventThroughParent(AccessibilityEvent event) {
        ViewParent parent = view.getParent();
        if (parent != null) parent.requestSendAccessibilityEvent(view, event);
    }
}
