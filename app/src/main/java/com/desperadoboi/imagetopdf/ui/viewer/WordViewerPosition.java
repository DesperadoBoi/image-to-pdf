package com.desperadoboi.imagetopdf.ui.viewer;

final class WordViewerPosition {
    private final int blockPosition;
    private final int offsetPixels;

    WordViewerPosition(int blockPosition, int offsetPixels) {
        this.blockPosition = Math.max(0, blockPosition);
        this.offsetPixels = offsetPixels;
    }

    int getBlockPosition() {
        return blockPosition;
    }

    int getOffsetPixels() {
        return offsetPixels;
    }

    WordViewerPosition clampToBlockCount(int blockCount) {
        return new WordViewerPosition(
                Math.min(blockPosition, Math.max(0, blockCount - 1)),
                offsetPixels
        );
    }
}
