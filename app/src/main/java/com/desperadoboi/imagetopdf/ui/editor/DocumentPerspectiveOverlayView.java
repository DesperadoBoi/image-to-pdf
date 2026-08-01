package com.desperadoboi.imagetopdf.ui.editor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.customview.widget.ExploreByTouchHelper;

import com.desperadoboi.imagetopdf.R;
import com.desperadoboi.imagetopdf.model.NormalizedPoint;
import com.desperadoboi.imagetopdf.model.PerspectiveQuad;
import com.desperadoboi.imagetopdf.ui.editor.geometry.NormalizedCoordinateMapper;
import com.desperadoboi.imagetopdf.ui.editor.geometry.PerspectiveQuadEditor;
import com.google.android.material.color.MaterialColors;

import java.util.List;

public final class DocumentPerspectiveOverlayView extends View {
    private static final PerspectiveQuadEditor.Handle[] HANDLES = {
            PerspectiveQuadEditor.Handle.TOP_LEFT,
            PerspectiveQuadEditor.Handle.TOP,
            PerspectiveQuadEditor.Handle.TOP_RIGHT,
            PerspectiveQuadEditor.Handle.RIGHT,
            PerspectiveQuadEditor.Handle.BOTTOM_RIGHT,
            PerspectiveQuadEditor.Handle.BOTTOM,
            PerspectiveQuadEditor.Handle.BOTTOM_LEFT,
            PerspectiveQuadEditor.Handle.LEFT
    };

    private final RectF imageRect = new RectF();
    private final Paint shadePaint = new Paint();
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path shadePath = new Path();
    private final Path quadPath = new Path();

    private final float handleRadius;
    private final float touchRadius;
    private final float accessibilityTargetSize;
    private final CornerAccessibilityHelper accessibilityHelper;

    private PerspectiveQuad quad = PerspectiveQuad.FULL;
    private PerspectiveQuad gestureStartQuad;
    private PerspectiveQuadEditor.Handle activeHandle;
    private boolean hasImageRect;
    private boolean edgeHandlesEnabled = true;

    public DocumentPerspectiveOverlayView(Context context) {
        this(context, null);
    }

    public DocumentPerspectiveOverlayView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DocumentPerspectiveOverlayView(
            Context context,
            @Nullable AttributeSet attrs,
            int defStyleAttr
    ) {
        super(context, attrs, defStyleAttr);
        handleRadius = getResources().getDimension(R.dimen.document_handle_radius);
        touchRadius = getResources().getDimension(R.dimen.document_handle_touch_radius);
        accessibilityTargetSize = Math.max(
                touchRadius * 2f,
                getResources().getDimension(R.dimen.touch_target)
        );
        shadePaint.setColor(Color.argb(160, 0, 0, 0));
        int overlayColor = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(getResources().getDimension(R.dimen.document_border_stroke));
        borderPaint.setColor(overlayColor);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(getResources().getDimension(R.dimen.document_grid_stroke));
        gridPaint.setColor(MaterialColors.getColor(
                this,
                com.google.android.material.R.attr.colorOnSurface
        ));
        gridPaint.setAlpha(180);
        handlePaint.setStyle(Paint.Style.FILL);
        handlePaint.setColor(overlayColor);
        setContentDescription(
                getResources().getString(R.string.document_overlay_content_description)
        );
        accessibilityHelper = new CornerAccessibilityHelper(this);
        ViewCompat.setAccessibilityDelegate(this, accessibilityHelper);
        setFocusable(true);
        setFocusableInTouchMode(true);
        ViewCompat.setImportantForAccessibility(this, ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    public void setImageContentRect(RectF contentRect) {
        if (contentRect == null
                || !Float.isFinite(contentRect.left)
                || !Float.isFinite(contentRect.top)
                || !Float.isFinite(contentRect.right)
                || !Float.isFinite(contentRect.bottom)
                || contentRect.width() <= 0f
                || contentRect.height() <= 0f) {
            clearImageContentRect();
            return;
        }
        imageRect.set(contentRect);
        hasImageRect = true;
        invalidate();
        accessibilityHelper.invalidateRoot();
    }

    public void clearImageContentRect() {
        hasImageRect = false;
        activeHandle = null;
        invalidate();
        accessibilityHelper.invalidateRoot();
    }

    public void setPerspectiveQuad(PerspectiveQuad quad) {
        if (quad == null) {
            throw new NullPointerException("quad is required");
        }
        this.quad = quad;
        invalidate();
        accessibilityHelper.invalidateRoot();
    }

    public PerspectiveQuad getPerspectiveQuad() {
        return quad;
    }

    public void setEdgeHandlesEnabled(boolean enabled) {
        edgeHandlesEnabled = enabled;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!hasImageRect) {
            return;
        }
        buildQuadPath();
        shadePath.reset();
        shadePath.setFillType(Path.FillType.EVEN_ODD);
        shadePath.addRect(0f, 0f, getWidth(), getHeight(), Path.Direction.CW);
        shadePath.addPath(quadPath);
        canvas.drawPath(shadePath, shadePaint);
        drawGrid(canvas);
        canvas.drawPath(quadPath, borderPaint);
        for (PerspectiveQuadEditor.Handle handle : HANDLES) {
            if (!edgeHandlesEnabled && !isCorner(handle)) {
                continue;
            }
            NormalizedPoint point = handlePoint(handle);
            canvas.drawCircle(viewX(point), viewY(point), handleRadius, handlePaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!hasImageRect) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                activeHandle = findNearestHandle(event.getX(), event.getY());
                if (activeHandle == null) {
                    return false;
                }
                gestureStartQuad = quad;
                requestParentIntercept(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                moveActiveHandle(event.getX(), event.getY());
                return true;
            case MotionEvent.ACTION_UP:
                moveActiveHandle(event.getX(), event.getY());
                activeHandle = null;
                performClick();
                requestParentIntercept(false);
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (gestureStartQuad != null) {
                    PerspectiveQuad cancelledQuad = quad;
                    quad = gestureStartQuad;
                    invalidateQuadChange(cancelledQuad, quad);
                    accessibilityHelper.invalidateRoot();
                }
                activeHandle = null;
                requestParentIntercept(false);
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    @Override
    public boolean dispatchHoverEvent(MotionEvent event) {
        return accessibilityHelper.dispatchHoverEvent(event) || super.dispatchHoverEvent(event);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            PerspectiveCornerAccessibilityModel.Direction direction = directionFor(event);
            int focusedId = accessibilityHelper.getKeyboardFocusedVirtualViewId();
            if (!PerspectiveCornerAccessibilityModel.isCornerId(focusedId)) {
                focusedId = accessibilityHelper.getAccessibilityFocusedVirtualViewId();
            }
            if (direction != null
                    && PerspectiveCornerAccessibilityModel.isCornerId(focusedId)) {
                float multiplier = event.isShiftPressed() ? 5f : 1f;
                moveAccessibleCorner(
                        focusedId,
                        direction,
                        PerspectiveCornerAccessibilityModel.DEFAULT_STEP * multiplier
                );
                // Do not move focus to another corner when this one reaches a boundary.
                return true;
            }
        }
        return accessibilityHelper.dispatchKeyEvent(event) || super.dispatchKeyEvent(event);
    }

    @Override
    protected void onFocusChanged(
            boolean gainFocus,
            int direction,
            @Nullable Rect previouslyFocusedRect
    ) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        accessibilityHelper.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
    }

    private void moveActiveHandle(float x, float y) {
        if (activeHandle == null) {
            return;
        }
        NormalizedPoint target = mapper().toNormalized(x, y);
        PerspectiveQuad previous = quad;
        PerspectiveQuad moved = PerspectiveQuadEditor.moveHandle(
                quad,
                activeHandle,
                target.getX(),
                target.getY()
        );
        if (moved.equals(previous)) {
            return;
        }
        quad = moved;
        invalidateQuadChange(previous, moved);
        accessibilityHelper.invalidateHandle(activeHandle);
    }

    private boolean moveAccessibleCorner(
            int virtualId,
            PerspectiveCornerAccessibilityModel.Direction direction,
            float step
    ) {
        if (!hasImageRect) return false;
        PerspectiveQuad previous = quad;
        PerspectiveQuad moved = PerspectiveCornerAccessibilityModel.move(
                previous,
                virtualId,
                direction,
                step
        );
        if (moved.equals(previous)) return false;
        quad = moved;
        invalidateQuadChange(previous, moved);
        accessibilityHelper.invalidateVirtualView(virtualId);
        accessibilityHelper.sendEventForVirtualView(
                virtualId,
                AccessibilityEvent.TYPE_ANNOUNCEMENT
        );
        return true;
    }

    private PerspectiveCornerAccessibilityModel.Direction directionFor(KeyEvent event) {
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return PerspectiveCornerAccessibilityModel.Direction.LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return PerspectiveCornerAccessibilityModel.Direction.RIGHT;
            case KeyEvent.KEYCODE_DPAD_UP:
                return PerspectiveCornerAccessibilityModel.Direction.UP;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return PerspectiveCornerAccessibilityModel.Direction.DOWN;
            default:
                return null;
        }
    }

    private void invalidateQuadChange(PerspectiveQuad before, PerspectiveQuad after) {
        if (!hasImageRect) {
            invalidate();
            return;
        }
        float left = Float.MAX_VALUE;
        float top = Float.MAX_VALUE;
        float right = -Float.MAX_VALUE;
        float bottom = -Float.MAX_VALUE;
        for (PerspectiveQuad candidate : new PerspectiveQuad[]{before, after}) {
            for (NormalizedPoint point : cornerPoints(candidate)) {
                float x = viewX(point);
                float y = viewY(point);
                left = Math.min(left, x);
                top = Math.min(top, y);
                right = Math.max(right, x);
                bottom = Math.max(bottom, y);
            }
        }
        float padding = Math.max(handleRadius, borderPaint.getStrokeWidth()) + 2f;
        invalidate(
                Math.max(0, (int) Math.floor(left - padding)),
                Math.max(0, (int) Math.floor(top - padding)),
                Math.min(getWidth(), (int) Math.ceil(right + padding)),
                Math.min(getHeight(), (int) Math.ceil(bottom + padding))
        );
    }

    private NormalizedPoint[] cornerPoints(PerspectiveQuad candidate) {
        return new NormalizedPoint[]{
                candidate.getTopLeft(),
                candidate.getTopRight(),
                candidate.getBottomRight(),
                candidate.getBottomLeft()
        };
    }

    private PerspectiveQuadEditor.Handle findNearestHandle(float x, float y) {
        PerspectiveQuadEditor.Handle nearest = null;
        float nearestDistanceSquared = touchRadius * touchRadius;
        for (PerspectiveQuadEditor.Handle handle : HANDLES) {
            if (!edgeHandlesEnabled && !isCorner(handle)) {
                continue;
            }
            NormalizedPoint point = handlePoint(handle);
            float dx = x - viewX(point);
            float dy = y - viewY(point);
            float distanceSquared = (dx * dx) + (dy * dy);
            if (distanceSquared <= nearestDistanceSquared) {
                nearestDistanceSquared = distanceSquared;
                nearest = handle;
            }
        }
        return nearest;
    }

    private boolean isCorner(PerspectiveQuadEditor.Handle handle) {
        return handle == PerspectiveQuadEditor.Handle.TOP_LEFT
                || handle == PerspectiveQuadEditor.Handle.TOP_RIGHT
                || handle == PerspectiveQuadEditor.Handle.BOTTOM_RIGHT
                || handle == PerspectiveQuadEditor.Handle.BOTTOM_LEFT;
    }

    private void buildQuadPath() {
        quadPath.reset();
        quadPath.moveTo(viewX(quad.getTopLeft()), viewY(quad.getTopLeft()));
        quadPath.lineTo(viewX(quad.getTopRight()), viewY(quad.getTopRight()));
        quadPath.lineTo(viewX(quad.getBottomRight()), viewY(quad.getBottomRight()));
        quadPath.lineTo(viewX(quad.getBottomLeft()), viewY(quad.getBottomLeft()));
        quadPath.close();
    }

    private void drawGrid(Canvas canvas) {
        for (int index = 1; index < 3; index++) {
            float fraction = index / 3f;
            NormalizedPoint top = interpolate(quad.getTopLeft(), quad.getTopRight(), fraction);
            NormalizedPoint bottom = interpolate(
                    quad.getBottomLeft(),
                    quad.getBottomRight(),
                    fraction
            );
            NormalizedPoint left = interpolate(quad.getTopLeft(), quad.getBottomLeft(), fraction);
            NormalizedPoint right = interpolate(
                    quad.getTopRight(),
                    quad.getBottomRight(),
                    fraction
            );
            canvas.drawLine(viewX(top), viewY(top), viewX(bottom), viewY(bottom), gridPaint);
            canvas.drawLine(viewX(left), viewY(left), viewX(right), viewY(right), gridPaint);
        }
    }

    private NormalizedPoint handlePoint(PerspectiveQuadEditor.Handle handle) {
        switch (handle) {
            case TOP_LEFT:
                return quad.getTopLeft();
            case TOP:
                return PerspectiveQuadEditor.midpoint(quad.getTopLeft(), quad.getTopRight());
            case TOP_RIGHT:
                return quad.getTopRight();
            case RIGHT:
                return PerspectiveQuadEditor.midpoint(quad.getTopRight(), quad.getBottomRight());
            case BOTTOM_RIGHT:
                return quad.getBottomRight();
            case BOTTOM:
                return PerspectiveQuadEditor.midpoint(quad.getBottomLeft(), quad.getBottomRight());
            case BOTTOM_LEFT:
                return quad.getBottomLeft();
            case LEFT:
                return PerspectiveQuadEditor.midpoint(quad.getTopLeft(), quad.getBottomLeft());
            default:
                throw new IllegalArgumentException("Unknown perspective handle");
        }
    }

    private NormalizedPoint interpolate(
            NormalizedPoint start,
            NormalizedPoint end,
            float fraction
    ) {
        return new NormalizedPoint(
                start.getX() + ((end.getX() - start.getX()) * fraction),
                start.getY() + ((end.getY() - start.getY()) * fraction)
        );
    }

    private float viewX(NormalizedPoint point) {
        return imageRect.left + (point.getX() * imageRect.width());
    }

    private float viewY(NormalizedPoint point) {
        return imageRect.top + (point.getY() * imageRect.height());
    }

    private NormalizedCoordinateMapper mapper() {
        return new NormalizedCoordinateMapper(
                imageRect.left,
                imageRect.top,
                imageRect.right,
                imageRect.bottom
        );
    }

    private void requestParentIntercept(boolean disallow) {
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(disallow);
        }
    }

    private final class CornerAccessibilityHelper extends ExploreByTouchHelper {
        private CornerAccessibilityHelper(View host) {
            super(host);
        }

        @Override
        protected int getVirtualViewAt(float x, float y) {
            if (!hasImageRect) return ExploreByTouchHelper.INVALID_ID;
            int virtualId = PerspectiveCornerAccessibilityModel.hitTest(
                    quad,
                    imageRect.left,
                    imageRect.top,
                    imageRect.right,
                    imageRect.bottom,
                    getWidth(),
                    getHeight(),
                    accessibilityTargetSize,
                    x,
                    y
            );
            return virtualId == PerspectiveCornerAccessibilityModel.INVALID_VIRTUAL_ID
                    ? ExploreByTouchHelper.INVALID_ID
                    : virtualId;
        }

        @Override
        protected void getVisibleVirtualViews(@NonNull List<Integer> virtualViewIds) {
            if (!hasImageRect) return;
            virtualViewIds.add(PerspectiveCornerAccessibilityModel.TOP_LEFT_ID);
            virtualViewIds.add(PerspectiveCornerAccessibilityModel.TOP_RIGHT_ID);
            virtualViewIds.add(PerspectiveCornerAccessibilityModel.BOTTOM_LEFT_ID);
            virtualViewIds.add(PerspectiveCornerAccessibilityModel.BOTTOM_RIGHT_ID);
        }

        @Override
        protected void onPopulateNodeForVirtualView(
                int virtualViewId,
                @NonNull AccessibilityNodeInfoCompat node
        ) {
            NormalizedPoint point = PerspectiveCornerAccessibilityModel.pointFor(
                    quad,
                    virtualViewId
            );
            node.setContentDescription(getResources().getString(
                    R.string.document_corner_position_description,
                    getResources().getString(labelFor(virtualViewId)),
                    PerspectiveCornerAccessibilityModel.positionPercent(point.getX()),
                    PerspectiveCornerAccessibilityModel.positionPercent(point.getY())
            ));
            node.setClassName(View.class.getName());
            node.setEnabled(isEnabled());
            node.setFocusable(true);
            PerspectiveCornerAccessibilityModel.Bounds bounds =
                    PerspectiveCornerAccessibilityModel.boundsFor(
                            quad,
                            virtualViewId,
                            imageRect.left,
                            imageRect.top,
                            imageRect.right,
                            imageRect.bottom,
                            getWidth(),
                            getHeight(),
                            accessibilityTargetSize
                    );
            node.setBoundsInParent(new Rect(
                    bounds.getLeft(),
                    bounds.getTop(),
                    bounds.getRight(),
                    bounds.getBottom()
            ));
            addMoveAction(
                    node,
                    R.id.accessibility_action_move_page_left,
                    R.string.document_corner_move_left
            );
            addMoveAction(
                    node,
                    R.id.accessibility_action_move_page_right,
                    R.string.document_corner_move_right
            );
            addMoveAction(
                    node,
                    R.id.accessibility_action_move_page_up,
                    R.string.document_corner_move_up
            );
            addMoveAction(
                    node,
                    R.id.accessibility_action_move_page_down,
                    R.string.document_corner_move_down
            );
        }

        @Override
        protected boolean onPerformActionForVirtualView(
                int virtualViewId,
                int action,
                @Nullable android.os.Bundle arguments
        ) {
            PerspectiveCornerAccessibilityModel.Direction direction;
            if (action == R.id.accessibility_action_move_page_left) {
                direction = PerspectiveCornerAccessibilityModel.Direction.LEFT;
            } else if (action == R.id.accessibility_action_move_page_right) {
                direction = PerspectiveCornerAccessibilityModel.Direction.RIGHT;
            } else if (action == R.id.accessibility_action_move_page_up) {
                direction = PerspectiveCornerAccessibilityModel.Direction.UP;
            } else if (action == R.id.accessibility_action_move_page_down) {
                direction = PerspectiveCornerAccessibilityModel.Direction.DOWN;
            } else {
                return false;
            }
            return moveAccessibleCorner(
                    virtualViewId,
                    direction,
                    PerspectiveCornerAccessibilityModel.DEFAULT_STEP
            );
        }

        private void addMoveAction(
                AccessibilityNodeInfoCompat node,
                int actionId,
                int labelId
        ) {
            node.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                    actionId,
                    getResources().getString(labelId)
            ));
        }

        private int labelFor(int virtualViewId) {
            switch (virtualViewId) {
                case PerspectiveCornerAccessibilityModel.TOP_LEFT_ID:
                    return R.string.document_corner_top_left;
                case PerspectiveCornerAccessibilityModel.TOP_RIGHT_ID:
                    return R.string.document_corner_top_right;
                case PerspectiveCornerAccessibilityModel.BOTTOM_LEFT_ID:
                    return R.string.document_corner_bottom_left;
                case PerspectiveCornerAccessibilityModel.BOTTOM_RIGHT_ID:
                    return R.string.document_corner_bottom_right;
                default:
                    throw new IllegalArgumentException("Unknown corner virtual id");
            }
        }

        private void invalidateHandle(PerspectiveQuadEditor.Handle handle) {
            switch (handle) {
                case TOP_LEFT:
                    invalidateVirtualView(PerspectiveCornerAccessibilityModel.TOP_LEFT_ID);
                    break;
                case TOP_RIGHT:
                    invalidateVirtualView(PerspectiveCornerAccessibilityModel.TOP_RIGHT_ID);
                    break;
                case BOTTOM_LEFT:
                    invalidateVirtualView(PerspectiveCornerAccessibilityModel.BOTTOM_LEFT_ID);
                    break;
                case BOTTOM_RIGHT:
                    invalidateVirtualView(PerspectiveCornerAccessibilityModel.BOTTOM_RIGHT_ID);
                    break;
                case TOP:
                    invalidateVirtualView(PerspectiveCornerAccessibilityModel.TOP_LEFT_ID);
                    invalidateVirtualView(PerspectiveCornerAccessibilityModel.TOP_RIGHT_ID);
                    break;
                case RIGHT:
                    invalidateVirtualView(PerspectiveCornerAccessibilityModel.TOP_RIGHT_ID);
                    invalidateVirtualView(PerspectiveCornerAccessibilityModel.BOTTOM_RIGHT_ID);
                    break;
                case BOTTOM:
                    invalidateVirtualView(PerspectiveCornerAccessibilityModel.BOTTOM_LEFT_ID);
                    invalidateVirtualView(PerspectiveCornerAccessibilityModel.BOTTOM_RIGHT_ID);
                    break;
                case LEFT:
                    invalidateVirtualView(PerspectiveCornerAccessibilityModel.TOP_LEFT_ID);
                    invalidateVirtualView(PerspectiveCornerAccessibilityModel.BOTTOM_LEFT_ID);
                    break;
                default:
                    invalidateRoot();
            }
        }
    }
}
