package com.desperadoboi.imagetopdf.model;

import java.util.List;
import java.util.Objects;

public final class PageOrderManager {
    private PageOrderManager() {
    }

    public static <T> boolean move(List<T> items, int fromPosition, int toPosition) {
        Objects.requireNonNull(items, "items is required");
        validatePosition(items, fromPosition, "fromPosition");
        validatePosition(items, toPosition, "toPosition");

        if (fromPosition == toPosition) {
            return false;
        }

        T movedItem = items.remove(fromPosition);
        items.add(toPosition, movedItem);
        return true;
    }

    private static void validatePosition(List<?> items, int position, String name) {
        if (position < 0 || position >= items.size()) {
            throw new IndexOutOfBoundsException(name + " is out of bounds: " + position);
        }
    }
}
