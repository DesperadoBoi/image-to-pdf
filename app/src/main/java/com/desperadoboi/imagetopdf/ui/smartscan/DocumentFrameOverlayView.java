package com.desperadoboi.imagetopdf.ui.smartscan;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.desperadoboi.imagetopdf.R;
import com.google.android.material.color.MaterialColors;

public final class DocumentFrameOverlayView extends View {
    private static final float PORTRAIT_ASPECT = 0.707f;
    private static final float LANDSCAPE_ASPECT = 1.414f;

    private final Paint shadePaint = new Paint();
    private final Paint contourPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cornerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path shadePath = new Path();
    private final RectF frame = new RectF();
    private final float safeInset;
    private final float cornerLength;

    public DocumentFrameOverlayView(Context context) {
        this(context, null);
    }

    public DocumentFrameOverlayView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DocumentFrameOverlayView(
            Context context,
            @Nullable AttributeSet attrs,
            int defStyleAttr
    ) {
        super(context, attrs, defStyleAttr);
        safeInset = getResources().getDimension(R.dimen.scan_frame_safe_inset);
        cornerLength = getResources().getDimension(R.dimen.scan_frame_corner_length);
        int accent = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary);
        shadePaint.setColor(Color.argb(118, 2, 6, 15));
        contourPaint.setStyle(Paint.Style.STROKE);
        contourPaint.setStrokeWidth(getResources().getDimension(R.dimen.scan_frame_stroke));
        contourPaint.setColor(accent);
        contourPaint.setAlpha(155);
        cornerPaint.setStyle(Paint.Style.STROKE);
        cornerPaint.setStrokeCap(Paint.Cap.SQUARE);
        cornerPaint.setStrokeWidth(getResources().getDimension(R.dimen.scan_frame_corner_stroke));
        cornerPaint.setColor(accent);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        updateFrame(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (frame.isEmpty()) {
            return;
        }
        shadePath.reset();
        shadePath.setFillType(Path.FillType.EVEN_ODD);
        shadePath.addRect(0f, 0f, getWidth(), getHeight(), Path.Direction.CW);
        shadePath.addRoundRect(frame, 8f, 8f, Path.Direction.CW);
        canvas.drawPath(shadePath, shadePaint);
        canvas.drawRoundRect(frame, 8f, 8f, contourPaint);
        drawCorners(canvas);
    }

    private void updateFrame(int width, int height) {
        float availableWidth = Math.max(0f, width - (safeInset * 2f));
        float availableHeight = Math.max(0f, height - (safeInset * 2f));
        if (availableWidth <= 0f || availableHeight <= 0f) {
            frame.setEmpty();
            return;
        }
        float targetAspect = height >= width ? PORTRAIT_ASPECT : LANDSCAPE_ASPECT;
        float frameWidth = availableWidth * 0.9f;
        float frameHeight = frameWidth / targetAspect;
        if (frameHeight > availableHeight * 0.9f) {
            frameHeight = availableHeight * 0.9f;
            frameWidth = frameHeight * targetAspect;
        }
        float left = (width - frameWidth) / 2f;
        float top = (height - frameHeight) / 2f;
        frame.set(left, top, left + frameWidth, top + frameHeight);
    }

    private void drawCorners(Canvas canvas) {
        drawCorner(canvas, frame.left, frame.top, 1f, 1f);
        drawCorner(canvas, frame.right, frame.top, -1f, 1f);
        drawCorner(canvas, frame.right, frame.bottom, -1f, -1f);
        drawCorner(canvas, frame.left, frame.bottom, 1f, -1f);
    }

    private void drawCorner(Canvas canvas, float x, float y, float horizontal, float vertical) {
        canvas.drawLine(x, y, x + (cornerLength * horizontal), y, cornerPaint);
        canvas.drawLine(x, y, x, y + (cornerLength * vertical), cornerPaint);
    }
}
