package com.desperadoboi.imagetopdf.ui.viewer;

import java.util.LinkedHashMap;
import java.util.Map;

final class SpreadsheetBoundedLruCache<K, V> {
    private final int maximumSize;
    private final LinkedHashMap<K, V> values;

    SpreadsheetBoundedLruCache(int maximumSize) {
        this.maximumSize = Math.max(1, maximumSize);
        values = new LinkedHashMap<K, V>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > SpreadsheetBoundedLruCache.this.maximumSize;
            }
        };
    }

    V get(K key) {
        return values.get(key);
    }

    void put(K key, V value) {
        values.put(key, value);
    }

    int size() {
        return values.size();
    }

    void clear() {
        values.clear();
    }
}
