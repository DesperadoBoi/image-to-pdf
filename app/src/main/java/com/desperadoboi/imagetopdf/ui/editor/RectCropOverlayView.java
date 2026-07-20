package com.desperadoboi.imagetopdf.ui.editor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import com.desperadoboi.imagetopdf.R;
import com.desperadoboi.imagetopdf.model.CropRect;
import com.desperadoboi.imagetopdf.model.NormalizedPoint;
import com.desperadoboi.imagetopdf.ui.editor.geometry.NormalizedCoordinateMapper;
import com.desperadoboi.imagetopdf.ui.editor.geometry.RectCropEditor;
import com.google.android.material.color.MaterialColors;

public final class RectCropOverlayView extends View {
    private final RectF imageRect = new RectF();
    private final RectF cropViewRect = new RectF();
    private final Paint shadePaint = new Paint();
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final float handleRadius;
    private final float touchRadius;
    private final float minimumCropSize;

    private CropRect cropRect = CropRect.FULL;
    private CropRect gestureStartCrop;
    private RectCropEditor.Handle activeHandle;
    private float lastNormalizedX;
    private float lastNormalizedY;
    private boolean hasImageRect;

    public RectCropOverlayView(Context context) {
        this(context, null);
    }

    public RectCropOverlayView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RectCropOverlayView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        handleRadius = getResources().getDimension(R.dimen.crop_handle_radius);
        touchRadius = getResources().getDimension(R.dimen.crop_handle_touch_radius);
        minimumCropSize = getResources().getDimension(R.dimen.crop_minimum_size);

        shadePaint.setColor(Color.argb(160, 0, 0, 0));
        int overlayColor = MaterialColors.getColor(
                this,
                androidx.appcompat.R.attr.colorPrimary
        );
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(getResources().getDimension(R.dimen.crop_border_stroke));
        borderPaint.setColor(overlayColor);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(getResources().getDimension(R.dimen.crop_grid_stroke));
        gridPaint.setColor(MaterialColors.getColor(
                this,
                com.google.android.material.R.attr.colorOnSurface
        ));
        gridPaint.setAlpha(180);
        handlePaint.setStyle(Paint.Style.FILL);
        handlePaint.setColor(overlayColor);
        setContentDescription(getResources().getString(R.string.crop_overlay_content_description));
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

    public void setCropRect(CropRect cropRect) {
        if (cropRect == null) {
            throw new NullPointerException("cropRect is required");
        }
        this.cropRect = cropRect;
        invalidate();
    }

    public CropRect getCropRect() {
        return cropRect;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!hasImageRect) {
            return;
        }
        updateCropViewRect();
        drawShade(canvas);
        drawGrid(canvas);
        canvas.drawRect(cropViewRect, borderPaint);
        drawHandles(canvas);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!hasImageRect) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                return beginGesture(event.getX(), event.getY());
            case MotionEvent.ACTION_MOVE:
                updateGesture(event.getX(), event.getY());
                return true;
            case MotionEvent.ACTION_UP:
                updateGesture(event.getX(), event.getY());
                activeHandle = null;
                performClick();
                requestParentIntercept(false);
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (gestureStartCrop != null) {
                    cropRect = gestureStartCrop;
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

    private boolean beginGesture(float x, float y) {
        updateCropViewRect();
        activeHandle = findNearestHandle(x, y);
        if (activeHandle == null && cropViewRect.contains(x, y)) {
            activeHandle = RectCropEditor.Handle.MOVE;
        }
        if (activeHandle == null) {
            return false;
        }
        gestureStartCrop = cropRect;
        NormalizedPoint point = mapper().toNormalized(x, y);
        lastNormalizedX = point.getX();
        lastNormalizedY = point.getY();
        requestParentIntercept(true);
        return true;
    }

    private void updateGesture(float x, float y) {
        if (activeHandle == null) {
            return;
        }
        NormalizedPoint point = mapper().toNormalized(x, y);
        if (activeHandle == RectCropEditor.Handle.MOVE) {
            cropRect = RectCropEditor.move(
                    cropRect,
                    point.getX() - lastNormalizedX,
                    point.getY() - lastNormalizedY
            );
            lastNormalizedX = point.getX();
            lastNormalizedY = point.getY();
        } else {
            cropRect = RectCropEditor.moveHandle(
                    cropRect,
                    activeHandle,
                    point.getX(),
                    point.getY(),
                    Math.min(1f, minimumCropSize / imageRect.width()),
                    Math.min(1f, minimumCropSize / imageRect.height())
            );
        }
        invalidate();
    }

    private RectCropEditor.Handle findNearestHandle(float x, float y) {
        RectCropEditor.Handle[] handles = resizeHandles();
        RectCropEditor.Handle nearest = null;
        float nearestDistanceSquared = touchRadius * touchRadius;
        for (RectCropEditor.Handle handle : handles) {
            float handleX = handleX(handle);
            float handleY = handleY(handle);
            float dx = x - handleX;
            float dy = y - handleY;
            float distanceSquared = (dx * dx) + (dy * dy);
            if (distanceSquared <= nearestDistanceSquared) {
                nearestDistanceSquared = distanceSquared;
                nearest = handle;
            }
        }
        return nearest;
    }

    private void drawShade(Canvas canvas) {
        canvas.drawRect(0f, 0f, getWidth(), cropViewRect.top, shadePaint);
        canvas.drawRect(0f, cropViewRect.bottom, getWidth(), getHeight(), shadePaint);
        canvas.drawRect(0f, cropViewRect.top, cropViewRect.left, cropViewRect.bottom, shadePaint);
        canvas.drawRect(
                cropViewRect.right,
                cropViewRect.top,
                getWidth(),
                cropViewRect.bottom,
                shadePaint
        );
    }

    private void drawGrid(Canvas canvas) {
        for (int index = 1; index < 3; index++) {
            float fraction = index / 3f;
            float x = cropViewRect.left + (cropViewRect.width() * fraction);
            float y = cropViewRect.top + (cropViewRect.height() * fraction);
            canvas.drawLine(x, cropViewRect.top, x, cropViewRect.bottom, gridPaint);
            canvas.drawLine(cropViewRect.left, y, cropViewRect.right, y, gridPaint);
        }
    }

    private void drawHandles(Canvas canvas) {
        for (RectCropEditor.Handle handle : resizeHandles()) {
            canvas.drawCircle(handleX(handle), handleY(handle), handleRadius, handlePaint);
        }
    }

    private void updateCropViewRect() {
        cropViewRect.set(
                imageRect.left + (cropRect.getLeft() * imageRect.width()),
                imageRect.top + (cropRect.getTop() * imageRect.height()),
                imageRect.left + (cropRect.getRight() * imageRect.width()),
                imageRect.top + (cropRect.getBottom() * imageRect.height())
        );
    }

    private float handleX(RectCropEditor.Handle handle) {
        switch (handle) {
            case TOP_LEFT:
            case CENTER_LEFT:
            case BOTTOM_LEFT:
                return cropViewRect.left;
            case TOP_RIGHT:
            case CENTER_RIGHT:
            case BOTTOM_RIGHT:
                return cropViewRect.right;
            default:
                return cropViewRect.centerX();
        }
    }

    private float handleY(RectCropEditor.Handle handle) {
        switch (handle) {
            case TOP_LEFT:
            case TOP_CENTER:
            case TOP_RIGHT:
                return cropViewRect.top;
            case BOTTOM_LEFT:
            case BOTTOM_CENTER:
            case BOTTOM_RIGHT:
                return cropViewRect.bottom;
            default:
                return cropViewRect.centerY();
        }
    }

    private NormalizedCoordinateMapper mapper() {
        return new NormalizedCoordinateMapper(
                imageRect.left,
                imageRect.top,
                imageRect.right,
                imageRect.bottom
        );
    }

    private RectCropEditor.Handle[] resizeHandles() {
        return new RectCropEditor.Handle[]{
                RectCropEditor.Handle.TOP_LEFT,
                RectCropEditor.Handle.TOP_CENTER,
                RectCropEditor.Handle.TOP_RIGHT,
                RectCropEditor.Handle.CENTER_RIGHT,
                RectCropEditor.Handle.BOTTOM_RIGHT,
                RectCropEditor.Handle.BOTTOM_CENTER,
                RectCropEditor.Handle.BOTTOM_LEFT,
                RectCropEditor.Handle.CENTER_LEFT
        };
    }

    private void requestParentIntercept(boolean disallow) {
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(disallow);
        }
    }
}
