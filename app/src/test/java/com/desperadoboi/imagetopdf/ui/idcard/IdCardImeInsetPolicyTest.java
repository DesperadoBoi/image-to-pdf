package com.desperadoboi.imagetopdf.ui.idcard;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class IdCardImeInsetPolicyTest {
    @Test public void hiddenKeyboardKeepsBasePaddingAndExistingSystemBar() {
        assertEquals(16, IdCardImeInsetPolicy.bottomPadding(16, 24, 0));
        assertEquals(16, IdCardImeInsetPolicy.bottomPadding(16, 24, 24));
    }

    @Test public void visibleKeyboardAddsOnlyInsetBeyondNavigationBar() {
        assertEquals(292, IdCardImeInsetPolicy.bottomPadding(16, 24, 300));
    }

    @Test public void gestureNavigationAndThreeButtonNavigationUseSamePolicy() {
        assertEquals(316, IdCardImeInsetPolicy.bottomPadding(16, 0, 300));
        assertEquals(268, IdCardImeInsetPolicy.bottomPadding(16, 48, 300));
    }

    @Test public void focusedWatermarkScrollsAboveImePaddingWithActionClearance() {
        assertEquals(
                484,
                IdCardImeInsetPolicy.scrollDeltaToReveal(
                        900, 1080, 1184, 400, 800, 0, 500
                )
        );
    }

    @Test public void visibleWatermarkDoesNotRequestExtraScroll() {
        assertEquals(
                0,
                IdCardImeInsetPolicy.scrollDeltaToReveal(
                        520, 640, 680, 400, 800, 16, 100
                )
        );
    }

    @Test public void targetAboveViewportRequestsUpwardScroll() {
        assertEquals(
                -96,
                IdCardImeInsetPolicy.scrollDeltaToReveal(
                        320, 400, 400, 400, 800, 16, 100
                )
        );
    }

    @Test public void compactLandscapePrioritizesFieldOverNearbyAction() {
        assertEquals(
                500,
                IdCardImeInsetPolicy.scrollDeltaToReveal(
                        900, 1100, 1300, 400, 300, 0, 240
                )
        );
    }

    @Test public void fieldFitsButActionDoesNotKeepsWholeFieldVisible() {
        assertEquals(
                400,
                IdCardImeInsetPolicy.scrollDeltaToReveal(
                        900, 1100, 1300, 400, 500, 0, 200
                )
        );
    }
}
