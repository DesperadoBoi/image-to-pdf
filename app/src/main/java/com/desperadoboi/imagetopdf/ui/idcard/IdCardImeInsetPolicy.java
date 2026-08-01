package com.desperadoboi.imagetopdf.ui.idcard;

public final class IdCardImeInsetPolicy {
    private IdCardImeInsetPolicy() {
    }

    public static int bottomPadding(
            int basePadding,
            int systemBarInset,
            int imeInset
    ) {
        int safeBase = Math.max(0, basePadding);
        int safeSystemBar = Math.max(0, systemBarInset);
        int safeIme = Math.max(0, imeInset);
        return safeBase + Math.max(0, safeIme - safeSystemBar);
    }

    public static int scrollDeltaToReveal(
            int targetTop,
            int targetBottom,
            int requestedBottom,
            int scrollY,
            int viewportHeight,
            int topPadding,
            int bottomPadding
    ) {
        int safeScrollY = Math.max(0, scrollY);
        int safeTopPadding = Math.max(0, topPadding);
        int safeBottomPadding = Math.max(0, bottomPadding);
        int visibleTop = safeScrollY + safeTopPadding;
        int visibleHeight = Math.max(
                0,
                viewportHeight - safeTopPadding - safeBottomPadding
        );
        int visibleBottom = visibleTop + visibleHeight;
        int essentialHeight = Math.max(0, targetBottom - targetTop);
        int requestedHeight = Math.max(0, requestedBottom - targetTop);
        int revealBottom;
        if (essentialHeight > visibleHeight) {
            return targetTop - visibleTop;
        } else if (requestedHeight <= visibleHeight) {
            revealBottom = requestedBottom;
        } else {
            revealBottom = targetBottom;
        }

        if (revealBottom > visibleBottom) {
            return revealBottom - visibleBottom;
        }
        if (targetTop < visibleTop) {
            return targetTop - visibleTop;
        }
        return 0;
    }
}
