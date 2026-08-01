package com.desperadoboi.imagetopdf.ui.idcard;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class IdCardPreviewLayout extends FrameLayout {
    private static final float HEIGHT_TO_WIDTH = 53.98f / 85.60f;

    public IdCardPreviewLayout(@NonNull Context context) {
        this(context, null);
    }

    public IdCardPreviewLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int desiredHeight = Math.max(1, Math.round(width * HEIGHT_TO_WIDTH));
        int height = resolveSize(desiredHeight, heightMeasureSpec);
        super.onMeasure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        );
    }
}
