package com.desperadoboi.imagetopdf.ui.smartscan;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.desperadoboi.imagetopdf.R;
import com.google.android.material.color.MaterialColors;

public final class ScanGridOverlayView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public ScanGridOverlayView(Context context) {
        this(context, null);
    }

    public ScanGridOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(getResources().getDimension(R.dimen.scan_grid_stroke));
        paint.setColor(MaterialColors.getColor(
                this,
                com.google.android.material.R.attr.colorOnSurface
        ));
        paint.setAlpha(90);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (int index = 1; index < 3; index++) {
            float x = getWidth() * index / 3f;
            float y = getHeight() * index / 3f;
            canvas.drawLine(x, 0f, x, getHeight(), paint);
            canvas.drawLine(0f, y, getWidth(), y, paint);
        }
    }
}
