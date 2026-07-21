package com.desperadoboi.imagetopdf.ui.smartscan;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.desperadoboi.imagetopdf.R;
import com.google.android.material.color.MaterialColors;

public final class LevelIndicatorView extends View {
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float pointRadius;
    private final int accentColor;
    private final int levelColor;

    private LevelState state = LevelState.UNAVAILABLE;
    private float horizontalOffset;

    public LevelIndicatorView(Context context) {
        this(context, null);
    }

    public LevelIndicatorView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        pointRadius = getResources().getDimension(R.dimen.scan_level_point_radius);
        accentColor = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary);
        levelColor = ContextCompat.getColor(context, R.color.semantic_success);
        linePaint.setStrokeWidth(getResources().getDimension(R.dimen.scan_level_line_stroke));
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setColor(MaterialColors.getColor(
                this,
                com.google.android.material.R.attr.colorOnSurface
        ));
        linePaint.setAlpha(150);
        pointPaint.setStyle(Paint.Style.FILL);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setLevel(LevelState state, float horizontalOffset) {
        this.state = state;
        this.horizontalOffset = Math.max(-1f, Math.min(1f, horizontalOffset));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (state == LevelState.UNAVAILABLE) {
            return;
        }
        float centerY = getHeight() / 2f;
        float startX = getPaddingLeft() + pointRadius;
        float endX = getWidth() - getPaddingRight() - pointRadius;
        canvas.drawLine(startX, centerY, endX, centerY, linePaint);
        float centerX = (startX + endX) / 2f;
        float travel = (endX - startX) / 2f;
        pointPaint.setColor(state == LevelState.LEVEL ? levelColor : accentColor);
        canvas.drawCircle(centerX + (travel * horizontalOffset), centerY, pointRadius, pointPaint);
    }
}
