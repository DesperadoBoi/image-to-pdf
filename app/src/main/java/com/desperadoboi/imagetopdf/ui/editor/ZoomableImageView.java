package com.desperadoboi.imagetopdf.ui.editor;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

public final class ZoomableImageView extends AppCompatImageView {
    private static final float MAX_EXTRA_SCALE = 4f;

    private final Matrix imageMatrix = new Matrix();
    private final RectF drawableRect = new RectF();
    private final RectF mappedRect = new RectF();
    private final ScaleGestureDetector scaleGestureDetector;
    private final GestureDetector gestureDetector;

    private float minScale = 1f;
    private float maxScale = MAX_EXTRA_SCALE;
    private float currentScale = 1f;
    private float lastTouchX;
    private float lastTouchY;
    private boolean dragging;
    private boolean gesturesEnabled = true;
    private OnSwipeListener onSwipeListener;

    public ZoomableImageView(Context context) {
        this(context, null);
    }

    public ZoomableImageView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ZoomableImageView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setScaleType(ScaleType.MATRIX);
        scaleGestureDetector = new ScaleGestureDetector(context, new ScaleListener());
        gestureDetector = new GestureDetector(context, new GestureListener());
    }

    @Override
    public void setImageBitmap(Bitmap bitmap) {
        super.setImageBitmap(bitmap);
        resetZoom();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        resetZoom();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!gesturesEnabled) {
            return false;
        }
        if (getDrawable() == null) {
            return super.onTouchEvent(event);
        }

        scaleGestureDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragging = true;
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                requestParentDisallowIntercept(true);
                break;
            case MotionEvent.ACTION_POINTER_UP:
                lastTouchX = event.getX(0);
                lastTouchY = event.getY(0);
                break;
            case MotionEvent.ACTION_MOVE:
                if (dragging && !scaleGestureDetector.isInProgress()) {
                    float currentX = event.getX();
                    float currentY = event.getY();
                    panBy(currentX - lastTouchX, currentY - lastTouchY);
                    lastTouchX = currentX;
                    lastTouchY = currentY;
                }
                break;
            case MotionEvent.ACTION_UP:
                performClick();
                dragging = false;
                constrainTranslation();
                requestParentDisallowIntercept(false);
                break;
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                constrainTranslation();
                requestParentDisallowIntercept(false);
                break;
            default:
                break;
        }
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    public void resetZoom() {
        if (!hasValidDrawableAndViewSize()) {
            imageMatrix.reset();
            setImageMatrix(imageMatrix);
            return;
        }

        int drawableWidth = getDrawable().getIntrinsicWidth();
        int drawableHeight = getDrawable().getIntrinsicHeight();
        float viewWidth = getWidth();
        float viewHeight = getHeight();
        minScale = Math.min(viewWidth / drawableWidth, viewHeight / drawableHeight);
        if (!isFinitePositive(minScale)) {
            minScale = 1f;
        }
        maxScale = minScale * MAX_EXTRA_SCALE;
        currentScale = minScale;

        float scaledWidth = drawableWidth * currentScale;
        float scaledHeight = drawableHeight * currentScale;
        float translateX = (viewWidth - scaledWidth) / 2f;
        float translateY = (viewHeight - scaledHeight) / 2f;
        imageMatrix.reset();
        imageMatrix.setScale(currentScale, currentScale);
        imageMatrix.postTranslate(translateX, translateY);
        setImageMatrix(imageMatrix);
    }

    public void setGesturesEnabled(boolean gesturesEnabled) {
        this.gesturesEnabled = gesturesEnabled;
        if (!gesturesEnabled) {
            dragging = false;
            requestParentDisallowIntercept(false);
        }
    }

    public boolean getImageContentRect(RectF outputRect) {
        if (outputRect == null || !hasValidDrawableAndViewSize()) {
            return false;
        }
        drawableRect.set(
                0f,
                0f,
                getDrawable().getIntrinsicWidth(),
                getDrawable().getIntrinsicHeight()
        );
        imageMatrix.mapRect(outputRect, drawableRect);
        return true;
    }

    private void panBy(float dx, float dy) {
        if (!isFinite(dx) || !isFinite(dy)) {
            return;
        }
        imageMatrix.postTranslate(dx, dy);
        constrainTranslation();
        setImageMatrix(imageMatrix);
    }

    private void zoomBy(float scaleFactor, float focusX, float focusY) {
        if (!isFinitePositive(scaleFactor) || !isFinite(focusX) || !isFinite(focusY)) {
            return;
        }
        float nextScale = clamp(currentScale * scaleFactor, minScale, maxScale);
        if (!isFinitePositive(nextScale)) {
            return;
        }
        float appliedScaleFactor = nextScale / currentScale;
        imageMatrix.postScale(appliedScaleFactor, appliedScaleFactor, focusX, focusY);
        currentScale = nextScale;
        constrainTranslation();
        setImageMatrix(imageMatrix);
    }

    private void constrainTranslation() {
        if (!hasValidDrawableAndViewSize()) {
            return;
        }

        drawableRect.set(
                0f,
                0f,
                getDrawable().getIntrinsicWidth(),
                getDrawable().getIntrinsicHeight()
        );
        imageMatrix.mapRect(mappedRect, drawableRect);

        float deltaX = calculateAxisDelta(mappedRect.left, mappedRect.right, getWidth());
        float deltaY = calculateAxisDelta(mappedRect.top, mappedRect.bottom, getHeight());
        if (deltaX != 0f || deltaY != 0f) {
            imageMatrix.postTranslate(deltaX, deltaY);
        }
    }

    private float calculateAxisDelta(float start, float end, float viewSize) {
        float contentSize = end - start;
        if (contentSize <= viewSize) {
            return (viewSize / 2f) - ((start + end) / 2f);
        }
        if (start > 0f) {
            return -start;
        }
        if (end < viewSize) {
            return viewSize - end;
        }
        return 0f;
    }

    private boolean hasValidDrawableAndViewSize() {
        return getDrawable() != null
                && getDrawable().getIntrinsicWidth() > 0
                && getDrawable().getIntrinsicHeight() > 0
                && getWidth() > 0
                && getHeight() > 0;
    }

    private float clamp(float value, float min, float max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private boolean isFinitePositive(float value) {
        return isFinite(value) && value > 0f;
    }

    private boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    private void requestParentDisallowIntercept(boolean disallowIntercept) {
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(disallowIntercept);
        }
    }

    private final class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            zoomBy(detector.getScaleFactor(), detector.getFocusX(), detector.getFocusY());
            return true;
        }
    }

    public void setOnSwipeListener(@Nullable OnSwipeListener onSwipeListener) {
        this.onSwipeListener = onSwipeListener;
    }

    private final class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDown(MotionEvent event) {
            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent event) {
            if (currentScale > minScale * 1.5f) {
                resetZoom();
            } else {
                zoomBy(2.5f, event.getX(), event.getY());
            }
            return true;
        }

        @Override
        public boolean onFling(
                MotionEvent first,
                MotionEvent second,
                float velocityX,
                float velocityY
        ) {
            if (onSwipeListener == null || first == null || second == null
                    || currentScale > minScale * 1.05f
                    || Math.abs(velocityX) < 500f
                    || Math.abs(second.getX() - first.getX()) < 96f
                    || Math.abs(second.getY() - first.getY())
                    > Math.abs(second.getX() - first.getX())) {
                return false;
            }
            if (second.getX() < first.getX()) {
                onSwipeListener.onSwipeLeft();
            } else {
                onSwipeListener.onSwipeRight();
            }
            return true;
        }
    }

    public interface OnSwipeListener {
        void onSwipeLeft();
        void onSwipeRight();
    }
}
