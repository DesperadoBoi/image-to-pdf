package com.desperadoboi.imagetopdf.ui.editor;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public final class PageReorderAccessibilityModelTest {
    @Test public void portraitMiddlePageOffersLeftAndRight() {
        assertEquals(
                Arrays.asList(
                        PageReorderAccessibilityModel.Direction.LEFT,
                        PageReorderAccessibilityModel.Direction.RIGHT
                ),
                PageReorderAccessibilityModel.availableActions(false, 1, 3)
        );
    }

    @Test public void landscapeMiddlePageOffersUpAndDown() {
        assertEquals(
                Arrays.asList(
                        PageReorderAccessibilityModel.Direction.UP,
                        PageReorderAccessibilityModel.Direction.DOWN
                ),
                PageReorderAccessibilityModel.availableActions(true, 1, 3)
        );
    }

    @Test public void firstPageHasNoBackwardAction() {
        assertEquals(
                Collections.singletonList(PageReorderAccessibilityModel.Direction.RIGHT),
                PageReorderAccessibilityModel.availableActions(false, 0, 3)
        );
        assertEquals(
                Collections.singletonList(PageReorderAccessibilityModel.Direction.DOWN),
                PageReorderAccessibilityModel.availableActions(true, 0, 3)
        );
    }

    @Test public void lastPageHasNoForwardAction() {
        assertEquals(
                Collections.singletonList(PageReorderAccessibilityModel.Direction.LEFT),
                PageReorderAccessibilityModel.availableActions(false, 2, 3)
        );
        assertEquals(
                Collections.singletonList(PageReorderAccessibilityModel.Direction.UP),
                PageReorderAccessibilityModel.availableActions(true, 2, 3)
        );
    }

    @Test public void moveDirectionsResolveToAdjacentPositions() {
        assertEquals(1, PageReorderAccessibilityModel.targetPosition(
                PageReorderAccessibilityModel.Direction.LEFT, 2));
        assertEquals(1, PageReorderAccessibilityModel.targetPosition(
                PageReorderAccessibilityModel.Direction.UP, 2));
        assertEquals(3, PageReorderAccessibilityModel.targetPosition(
                PageReorderAccessibilityModel.Direction.RIGHT, 2));
        assertEquals(3, PageReorderAccessibilityModel.targetPosition(
                PageReorderAccessibilityModel.Direction.DOWN, 2));
    }

    @Test public void announcementUsesOneBasedPageNumber() {
        assertEquals(2, PageReorderAccessibilityModel.pageNumberForPosition(1));
    }
}
