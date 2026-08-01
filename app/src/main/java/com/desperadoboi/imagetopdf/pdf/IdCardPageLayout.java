package com.desperadoboi.imagetopdf.pdf;

import com.desperadoboi.imagetopdf.ui.idcard.IdCardSide;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class IdCardPageLayout {
    private final int pageWidth;
    private final int pageHeight;
    private final List<Placement> placements;

    public IdCardPageLayout(int pageWidth, int pageHeight, List<Placement> placements) {
        if (pageWidth <= 0 || pageHeight <= 0) {
            throw new IllegalArgumentException("page dimensions must be positive");
        }
        this.pageWidth = pageWidth;
        this.pageHeight = pageHeight;
        this.placements = Collections.unmodifiableList(new ArrayList<>(placements));
    }

    public int getPageWidth() {
        return pageWidth;
    }

    public int getPageHeight() {
        return pageHeight;
    }

    public List<Placement> getPlacements() {
        return placements;
    }

    public static final class Placement {
        private final IdCardSide side;
        private final float left;
        private final float top;
        private final float right;
        private final float bottom;

        public Placement(
                IdCardSide side,
                float left,
                float top,
                float right,
                float bottom
        ) {
            this.side = Objects.requireNonNull(side, "side is required");
            if (left < 0f || top < 0f || right <= left || bottom <= top) {
                throw new IllegalArgumentException("placement must have positive bounds");
            }
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        public IdCardSide getSide() {
            return side;
        }

        public float getLeft() {
            return left;
        }

        public float getTop() {
            return top;
        }

        public float getRight() {
            return right;
        }

        public float getBottom() {
            return bottom;
        }

        public float getWidth() {
            return right - left;
        }

        public float getHeight() {
            return bottom - top;
        }
    }
}
