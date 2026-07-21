package com.desperadoboi.imagetopdf.ui.editor;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.desperadoboi.imagetopdf.R;
import com.google.android.material.card.MaterialCardView;

public final class PdfSuccessBanner extends MaterialCardView {
    private static final long ENTER_DURATION_MS = 220L;
    private static final long VISIBLE_DURATION_MS = 3000L;
    private static final long EXIT_DURATION_MS = 180L;

    private TextView summaryView;
    private View openAction;
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
        openAction = findViewById(R.id.button_pdf_success_open);
    }

    public void showResult(String summary, OnClickListener openListener) {
        removeCallbacks(hideRunnable);
        animate().cancel();
        summaryView.setText(summary);
        setContentDescription(getContext().getString(
                R.string.pdf_success_banner_announcement,
                summary
        ));
        setOnClickListener(openListener);
        openAction.setOnClickListener(openListener);
        setVisibility(VISIBLE);
        setAlpha(0f);
        setTranslationY(-getResources().getDimension(R.dimen.pdf_success_banner_translation));
        animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(ENTER_DURATION_MS)
                .withEndAction(() -> postDelayed(hideRunnable, VISIBLE_DURATION_MS))
                .start();
    }

    public void hideImmediately() {
        removeCallbacks(hideRunnable);
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
                .translationY(-getResources().getDimension(R.dimen.pdf_success_banner_translation))
                .setDuration(EXIT_DURATION_MS)
                .withEndAction(this::hideImmediately)
                .start();
    }
}
