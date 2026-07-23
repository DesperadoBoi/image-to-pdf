package com.desperadoboi.imagetopdf.ui.viewer;

import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;

import java.util.Objects;

final class SpreadsheetTextLayoutCache {
    static final int MAXIMUM_LAYOUT_COUNT = 96;
    private static final int WIDTH_BUCKET_SIZE_PX = 4;
    private static final float ZOOM_BUCKETS_PER_UNIT = 20f;

    private final SpreadsheetBoundedLruCache<Key, StaticLayout> cache =
            new SpreadsheetBoundedLruCache<>(MAXIMUM_LAYOUT_COUNT);
    private long hits;
    private long misses;

    StaticLayout getOrCreate(
            int sheetId,
            int row,
            int column,
            String text,
            int styleKey,
            int width,
            int maximumLines,
            float scale,
            TextPaint sourcePaint,
            Layout.Alignment alignment
    ) {
        int safeWidth = Math.max(1, width);
        int safeMaximumLines = Math.max(1, maximumLines);
        Key key = new Key(
                sheetId,
                row,
                column,
                text,
                styleKey,
                widthBucket(safeWidth),
                zoomBucket(scale),
                safeMaximumLines,
                alignment
        );
        StaticLayout layout = cache.get(key);
        if (layout != null) {
            hits++;
            return layout;
        }
        misses++;
        TextPaint layoutPaint = new TextPaint(sourcePaint);
        layout = StaticLayout.Builder.obtain(text, 0, text.length(), layoutPaint, safeWidth)
                .setAlignment(alignment)
                .setIncludePad(false)
                .setLineSpacing(0f, 1f)
                .setMaxLines(safeMaximumLines)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setEllipsizedWidth(safeWidth)
                .build();
        cache.put(key, layout);
        return layout;
    }

    long getHits() {
        return hits;
    }

    long getMisses() {
        return misses;
    }

    int size() {
        return cache.size();
    }

    void clear() {
        cache.clear();
        hits = 0L;
        misses = 0L;
    }

    static int widthBucket(int width) {
        return Math.max(1, Math.round((float) width / WIDTH_BUCKET_SIZE_PX));
    }

    static int zoomBucket(float scale) {
        if (!Float.isFinite(scale)) return Math.round(ZOOM_BUCKETS_PER_UNIT);
        return Math.max(1, Math.round(scale * ZOOM_BUCKETS_PER_UNIT));
    }

    private static final class Key {
        private final int sheetId;
        private final int row;
        private final int column;
        private final String text;
        private final int styleKey;
        private final int widthBucket;
        private final int zoomBucket;
        private final int maximumLines;
        private final Layout.Alignment alignment;

        private Key(
                int sheetId,
                int row,
                int column,
                String text,
                int styleKey,
                int widthBucket,
                int zoomBucket,
                int maximumLines,
                Layout.Alignment alignment
        ) {
            this.sheetId = sheetId;
            this.row = row;
            this.column = column;
            this.text = text;
            this.styleKey = styleKey;
            this.widthBucket = widthBucket;
            this.zoomBucket = zoomBucket;
            this.maximumLines = maximumLines;
            this.alignment = alignment;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Key)) return false;
            Key key = (Key) other;
            return sheetId == key.sheetId
                    && row == key.row
                    && column == key.column
                    && styleKey == key.styleKey
                    && widthBucket == key.widthBucket
                    && zoomBucket == key.zoomBucket
                    && maximumLines == key.maximumLines
                    && text.equals(key.text)
                    && alignment == key.alignment;
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    sheetId,
                    row,
                    column,
                    text,
                    styleKey,
                    widthBucket,
                    zoomBucket,
                    maximumLines,
                    alignment
            );
        }
    }
}
