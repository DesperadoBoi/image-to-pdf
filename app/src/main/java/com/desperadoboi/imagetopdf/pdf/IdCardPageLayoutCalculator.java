package com.desperadoboi.imagetopdf.pdf;

import com.desperadoboi.imagetopdf.ui.idcard.IdCardExportPreset;
import com.desperadoboi.imagetopdf.ui.idcard.IdCardSide;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class IdCardPageLayoutCalculator {
    public static final int A4_WIDTH_POINTS = 595;
    public static final int A4_HEIGHT_POINTS = 842;
    public static final double POINTS_PER_MM = 72d / 25.4d;
    public static final double ID_1_WIDTH_MM = 85.60d;
    public static final double ID_1_HEIGHT_MM = 53.98d;

    private static final float EASY_MARGIN_POINTS = 48f;
    private static final float GAP_POINTS = 28f;

    private IdCardPageLayoutCalculator() {
    }

    public static IdCardPageLayout calculate(
            IdCardExportPreset preset,
            List<IdCardSide> sides
    ) {
        Objects.requireNonNull(preset, "preset is required");
        if (sides == null || sides.isEmpty() || sides.size() > 2) {
            throw new IllegalArgumentException("one or two ID-card sides are required");
        }

        float width;
        float height;
        if (preset == IdCardExportPreset.ACTUAL_SIZE) {
            width = (float) millimetersToPoints(ID_1_WIDTH_MM);
            height = (float) millimetersToPoints(ID_1_HEIGHT_MM);
        } else {
            width = A4_WIDTH_POINTS - (EASY_MARGIN_POINTS * 2f);
            height = width * (float) (ID_1_HEIGHT_MM / ID_1_WIDTH_MM);
        }

        float groupHeight = (height * sides.size())
                + (sides.size() == 2 ? GAP_POINTS : 0f);
        float top = (A4_HEIGHT_POINTS - groupHeight) / 2f;
        float left = (A4_WIDTH_POINTS - width) / 2f;
        ArrayList<IdCardPageLayout.Placement> placements = new ArrayList<>(sides.size());
        for (int index = 0; index < sides.size(); index++) {
            float placementTop = top + (index * (height + GAP_POINTS));
            placements.add(new IdCardPageLayout.Placement(
                    sides.get(index),
                    left,
                    placementTop,
                    left + width,
                    placementTop + height
            ));
        }
        validateWithinPage(placements);
        return new IdCardPageLayout(A4_WIDTH_POINTS, A4_HEIGHT_POINTS, placements);
    }

    public static double millimetersToPoints(double millimeters) {
        if (!(millimeters > 0d)) {
            throw new IllegalArgumentException("millimeters must be positive");
        }
        return millimeters * POINTS_PER_MM;
    }

    private static void validateWithinPage(List<IdCardPageLayout.Placement> placements) {
        for (IdCardPageLayout.Placement placement : placements) {
            if (placement.getRight() > A4_WIDTH_POINTS
                    || placement.getBottom() > A4_HEIGHT_POINTS) {
                throw new IllegalStateException("ID-card placement exceeds A4");
            }
        }
    }
}
