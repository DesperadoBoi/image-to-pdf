package com.desperadoboi.imagetopdf.document.pdf;

import java.util.LinkedHashMap;
import java.util.Map;

public final class PageLruCache<T> {
    private final int capacity;
    private final RemovalListener<T> removalListener;
    private final LinkedHashMap<Integer, T> values = new LinkedHashMap<>(4, 0.75f, true);

    public PageLruCache(int capacity, RemovalListener<T> removalListener) {
        if (capacity <= 0) throw new IllegalArgumentException("Capacity must be positive");
        this.capacity = capacity;
        this.removalListener = removalListener;
    }

    public synchronized T get(int page) { return values.get(page); }

    public synchronized void put(int page, T value) {
        T replaced = values.put(page, value);
        if (replaced != null && replaced != value) notifyRemoved(replaced);
        while (values.size() > capacity) {
            Map.Entry<Integer, T> eldest = values.entrySet().iterator().next();
            values.remove(eldest.getKey());
            notifyRemoved(eldest.getValue());
        }
    }

    public synchronized int size() { return values.size(); }

    public synchronized void clear() {
        for (T value : values.values()) notifyRemoved(value);
        values.clear();
    }

    private void notifyRemoved(T value) {
        if (removalListener != null && value != null) removalListener.onRemoved(value);
    }

    public interface RemovalListener<T> {
        void onRemoved(T value);
    }
}
