package com.desperadoboi.imagetopdf.ui.result;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.desperadoboi.imagetopdf.R;
import com.google.android.material.card.MaterialCardView;

public final class PdfSuccessBanner extends MaterialCardView {
    private static final long ENTER_DURATION_MS = 300L;
    private static final long VISIBLE_DURATION_MS = 2500L;
    private static final long EXIT_DURATION_MS = 240L;

    private TextView summaryView;
    private Runnable pendingShow;
    private final Runnable hideRunnable = this::hideAnimated;

    public PdfSuccessBanner(@NonNull Context context) {
        this(context, null);
    }

    public PdfSuccessBanner(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PdfSuccessBanner(
            @NonNull Context context,
            @Nullable AttributeSet attrs,
            int defStyleAttr
    ) {
        super(context, attrs, defStyleAttr);
        setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        summaryView = findViewById(R.id.text_pdf_success_summary);
    }

    public void showResult(String summary) {
        hideImmediately();
        summaryView.setText(summary);
        setContentDescription(getContext().getString(
                R.string.pdf_success_banner_announcement,
                summary
        ));
        setVisibility(INVISIBLE);
        pendingShow = () -> {
            pendingShow = null;
            if (!isAttachedToWindow()) {
                hideImmediately();
                return;
            }
            setTranslationY(-getHeight());
            setAlpha(0f);
            setVisibility(VISIBLE);
            announceForAccessibility(getContext().getString(
                    R.string.pdf_success_banner_content_description
            ));
            animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(ENTER_DURATION_MS)
                    .setInterpolator(new DecelerateInterpolator())
                    .withEndAction(() -> postDelayed(hideRunnable, VISIBLE_DURATION_MS))
                    .start();
        };
        post(pendingShow);
    }

    public void hideImmediately() {
        removeCallbacks(hideRunnable);
        if (pendingShow != null) {
            removeCallbacks(pendingShow);
            pendingShow = null;
        }
        animate().cancel();
        setVisibility(GONE);
        setAlpha(1f);
        setTranslationY(0f);
    }

    @Override
    protected void onDetachedFromWindow() {
        hideImmediately();
        super.onDetachedFromWindow();
    }

    private void hideAnimated() {
        if (getVisibility() != VISIBLE) {
            return;
        }
        animate()
                .alpha(0f)
                .translationY(-getHeight())
                .setDuration(EXIT_DURATION_MS)
                .withEndAction(this::hideImmediately)
                .start();
    }
}
