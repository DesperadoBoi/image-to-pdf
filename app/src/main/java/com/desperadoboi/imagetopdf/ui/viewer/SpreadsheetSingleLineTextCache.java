package com.desperadoboi.imagetopdf.ui.viewer;

final class SpreadsheetSingleLineTextCache {
    static final int MAXIMUM_ENTRY_COUNT = 128;

    private final SpreadsheetBoundedLruCache<String, String> cache =
            new SpreadsheetBoundedLruCache<>(MAXIMUM_ENTRY_COUNT);

    String get(String text) {
        if (text.indexOf('\n') < 0 && text.indexOf('\r') < 0) return text;
        String cached = cache.get(text);
        if (cached != null) return cached;
        char[] characters = text.toCharArray();
        for (int index = 0; index < characters.length; index++) {
            if (characters[index] == '\n' || characters[index] == '\r') {
                characters[index] = ' ';
            }
        }
        String singleLine = new String(characters);
        cache.put(text, singleLine);
        return singleLine;
    }

    int size() {
        return cache.size();
    }

    void clear() {
        cache.clear();
    }
}
