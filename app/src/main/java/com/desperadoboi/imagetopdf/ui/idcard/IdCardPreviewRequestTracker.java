package com.desperadoboi.imagetopdf.ui.idcard;

import java.util.EnumMap;

public final class IdCardPreviewRequestTracker {
    private final EnumMap<IdCardSide, String> activeKeys = new EnumMap<>(IdCardSide.class);

    public boolean start(IdCardSide side, String key) {
        if (side == null || key == null || key.isEmpty()) {
            throw new IllegalArgumentException("Side and key are required");
        }
        if (key.equals(activeKeys.get(side))) return false;
        activeKeys.put(side, key);
        return true;
    }

    public boolean isCurrent(IdCardSide side, String key) {
        return key != null && key.equals(activeKeys.get(side));
    }

    public void clear(IdCardSide side) {
        activeKeys.remove(side);
    }

    public void clearAll() {
        activeKeys.clear();
    }
}
