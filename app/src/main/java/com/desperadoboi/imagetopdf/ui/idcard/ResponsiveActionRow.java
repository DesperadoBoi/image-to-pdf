package com.desperadoboi.imagetopdf.ui.idcard;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class ResponsiveActionRow extends LinearLayout {
    private final List<OriginalLayout> originalLayouts = new ArrayList<>();
    private final int minimumStackedGap;
    private boolean stacked;

    public ResponsiveActionRow(Context context) {
        this(context, null);
    }

    public ResponsiveActionRow(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ResponsiveActionRow(
            Context context,
            @Nullable AttributeSet attrs,
            int defStyleAttr
    ) {
        super(context, attrs, defStyleAttr);
        minimumStackedGap = Math.round(4f * getResources().getDisplayMetrics().density);
        setOrientation(HORIZONTAL);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        originalLayouts.clear();
        for (int index = 0; index < getChildCount(); index++) {
            LayoutParams params = (LayoutParams) getChildAt(index).getLayoutParams();
            originalLayouts.add(new OriginalLayout(params));
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int availableWidth = Math.max(
                0,
                MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight()
        );
        int requiredWidth = measureRequiredHorizontalWidth(heightMeasureSpec);
        boolean shouldStack = widthMode != MeasureSpec.UNSPECIFIED
                && shouldStack(availableWidth, requiredWidth);
        applyOrientation(shouldStack);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    public static boolean shouldStack(int availableWidth, int requiredWidth) {
        return availableWidth > 0 && requiredWidth > availableWidth;
    }

    private int measureRequiredHorizontalWidth(int parentHeightMeasureSpec) {
        int requiredWidth = 0;
        int visibleIndex = 0;
        for (int index = 0; index < getChildCount(); index++) {
            View child = getChildAt(index);
            if (child.getVisibility() == GONE) continue;
            OriginalLayout original = originalLayouts.get(index);
            int childHeightSpec = getChildMeasureSpec(
                    parentHeightMeasureSpec,
                    getPaddingTop() + getPaddingBottom()
                            + original.topMargin + original.bottomMargin,
                    original.height
            );
            child.measure(
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                    childHeightSpec
            );
            requiredWidth += Math.max(child.getMeasuredWidth(), child.getMinimumWidth());
            requiredWidth += original.startMargin + original.endMargin;
            if (visibleIndex > 0 && original.startMargin == 0 && original.endMargin == 0) {
                requiredWidth += minimumStackedGap;
            }
            visibleIndex++;
        }
        return requiredWidth;
    }

    private void applyOrientation(boolean shouldStack) {
        stacked = shouldStack;
        int requestedOrientation = stacked ? VERTICAL : HORIZONTAL;
        if (getOrientation() != requestedOrientation) setOrientation(requestedOrientation);
        int visibleIndex = 0;
        for (int index = 0; index < getChildCount(); index++) {
            View child = getChildAt(index);
            OriginalLayout original = originalLayouts.get(index);
            LayoutParams params = (LayoutParams) child.getLayoutParams();
            if (stacked) {
                params.width = ViewGroup.LayoutParams.MATCH_PARENT;
                params.weight = 0f;
                params.setMarginStart(0);
                params.setMarginEnd(0);
                params.topMargin = child.getVisibility() == GONE || visibleIndex == 0
                        ? original.topMargin
                        : Math.max(minimumStackedGap, original.startMargin);
            } else {
                original.restore(params);
            }
            if (child.getVisibility() != GONE) visibleIndex++;
        }
    }

    private static final class OriginalLayout {
        private final int width;
        private final int height;
        private final float weight;
        private final int startMargin;
        private final int topMargin;
        private final int endMargin;
        private final int bottomMargin;

        private OriginalLayout(LayoutParams params) {
            width = params.width;
            height = params.height;
            weight = params.weight;
            startMargin = params.getMarginStart();
            topMargin = params.topMargin;
            endMargin = params.getMarginEnd();
            bottomMargin = params.bottomMargin;
        }

        private void restore(LayoutParams params) {
            params.width = width;
            params.height = height;
            params.weight = weight;
            params.setMarginStart(startMargin);
            params.topMargin = topMargin;
            params.setMarginEnd(endMargin);
            params.bottomMargin = bottomMargin;
        }
    }
}
