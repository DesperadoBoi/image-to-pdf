package com.desperadoboi.imagetopdf.ui.editor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import com.desperadoboi.imagetopdf.R;
import com.desperadoboi.imagetopdf.model.NormalizedPoint;
import com.desperadoboi.imagetopdf.model.PerspectiveQuad;
import com.desperadoboi.imagetopdf.ui.editor.geometry.NormalizedCoordinateMapper;
import com.desperadoboi.imagetopdf.ui.editor.geometry.PerspectiveQuadEditor;
import com.google.android.material.color.MaterialColors;

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

    private PerspectiveQuad quad = PerspectiveQuad.FULL;
    private PerspectiveQuad gestureStartQuad;
    private PerspectiveQuadEditor.Handle activeHandle;
    private boolean hasImageRect;

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
    }

    public void clearImageContentRect() {
        hasImageRect = false;
        activeHandle = null;
        invalidate();
    }

    public void setPerspectiveQuad(PerspectiveQuad quad) {
        if (quad == null) {
            throw new NullPointerException("quad is required");
        }
        this.quad = quad;
        invalidate();
    }

    public PerspectiveQuad getPerspectiveQuad() {
        return quad;
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
                    quad = gestureStartQuad;
                    invalidate();
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

    private void moveActiveHandle(float x, float y) {
        if (activeHandle == null) {
            return;
        }
        NormalizedPoint target = mapper().toNormalized(x, y);
        quad = PerspectiveQuadEditor.moveHandle(
                quad,
                activeHandle,
                target.getX(),
                target.getY()
        );
        invalidate();
    }

    private PerspectiveQuadEditor.Handle findNearestHandle(float x, float y) {
        PerspectiveQuadEditor.Handle nearest = null;
        float nearestDistanceSquared = touchRadius * touchRadius;
        for (PerspectiveQuadEditor.Handle handle : HANDLES) {
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
}
