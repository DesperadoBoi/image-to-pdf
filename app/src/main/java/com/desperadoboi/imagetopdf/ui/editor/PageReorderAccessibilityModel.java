package com.desperadoboi.imagetopdf.ui.editor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PageReorderAccessibilityModel {
    private PageReorderAccessibilityModel() {
    }

    public static List<Direction> availableActions(
            boolean vertical,
            int position,
            int pageCount
    ) {
        if (position < 0 || position >= pageCount) {
            return Collections.emptyList();
        }
        ArrayList<Direction> actions = new ArrayList<>(2);
        if (position > 0) {
            actions.add(vertical ? Direction.UP : Direction.LEFT);
        }
        if (position < pageCount - 1) {
            actions.add(vertical ? Direction.DOWN : Direction.RIGHT);
        }
        return actions;
    }

    public static int targetPosition(Direction direction, int currentPosition) {
        switch (direction) {
            case LEFT:
            case UP:
                return currentPosition - 1;
            case RIGHT:
            case DOWN:
                return currentPosition + 1;
            default:
                throw new IllegalArgumentException("Unsupported direction: " + direction);
        }
    }

    public static int pageNumberForPosition(int position) {
        return position + 1;
    }

    public enum Direction {
        LEFT,
        RIGHT,
        UP,
        DOWN
    }
}
