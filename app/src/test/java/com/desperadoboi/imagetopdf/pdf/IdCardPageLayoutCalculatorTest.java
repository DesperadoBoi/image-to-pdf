package com.desperadoboi.imagetopdf.pdf;

import com.desperadoboi.imagetopdf.model.ImagePlacementMode;
import com.desperadoboi.imagetopdf.ui.idcard.IdCardExportPreset;
import com.desperadoboi.imagetopdf.ui.idcard.IdCardSide;
import com.desperadoboi.imagetopdf.util.ImagePlacementCalculator;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class IdCardPageLayoutCalculatorTest {
    private static final float DELTA = 0.02f;

    @Test public void easyToReadOneSideIsCenteredOnA4() {
        IdCardPageLayout layout = IdCardPageLayoutCalculator.calculate(
                IdCardExportPreset.EASY_TO_READ,
                Collections.singletonList(IdCardSide.FRONT)
        );
        IdCardPageLayout.Placement side = layout.getPlacements().get(0);

        assertEquals(595, layout.getPageWidth());
        assertEquals(842, layout.getPageHeight());
        assertEquals(1, layout.getPlacements().size());
        assertEquals(layout.getPageWidth() / 2f, (side.getLeft() + side.getRight()) / 2f, DELTA);
        assertEquals(layout.getPageHeight() / 2f, (side.getTop() + side.getBottom()) / 2f, DELTA);
    }

    @Test public void easyToReadTwoSidesHaveEqualWidthAndGap() {
        IdCardPageLayout layout = twoSideLayout(IdCardExportPreset.EASY_TO_READ);
        IdCardPageLayout.Placement front = layout.getPlacements().get(0);
        IdCardPageLayout.Placement back = layout.getPlacements().get(1);

        assertEquals(front.getWidth(), back.getWidth(), DELTA);
        assertEquals(front.getHeight(), back.getHeight(), DELTA);
        assertTrue(back.getTop() > front.getBottom());
        assertEquals(front.getLeft(), back.getLeft(), DELTA);
    }

    @Test public void actualSizeUsesIdOnePhysicalWidth() {
        IdCardPageLayout.Placement side = twoSideLayout(IdCardExportPreset.ACTUAL_SIZE)
                .getPlacements().get(0);

        assertEquals(
                IdCardPageLayoutCalculator.millimetersToPoints(85.60d),
                side.getWidth(),
                DELTA
        );
    }

    @Test public void actualSizeUsesIdOnePhysicalHeight() {
        IdCardPageLayout.Placement side = twoSideLayout(IdCardExportPreset.ACTUAL_SIZE)
                .getPlacements().get(0);

        assertEquals(
                IdCardPageLayoutCalculator.millimetersToPoints(53.98d),
                side.getHeight(),
                DELTA
        );
    }

    @Test public void millimetersConvertUsingPdfPointsNotAndroidDensity() {
        assertEquals(72d, IdCardPageLayoutCalculator.millimetersToPoints(25.4d), 0.0001d);
    }

    @Test public void cardAspectRatioIsPreservedByBothPresets() {
        double expected = 85.60d / 53.98d;
        for (IdCardExportPreset preset : IdCardExportPreset.values()) {
            IdCardPageLayout.Placement side = twoSideLayout(preset).getPlacements().get(0);
            assertEquals(expected, side.getWidth() / side.getHeight(), 0.0002d);
        }
    }

    @Test public void easyLayoutKeepsSufficientPageMargins() {
        IdCardPageLayout layout = twoSideLayout(IdCardExportPreset.EASY_TO_READ);
        for (IdCardPageLayout.Placement side : layout.getPlacements()) {
            assertTrue(side.getLeft() >= 48f);
            assertTrue(side.getRight() <= layout.getPageWidth() - 48f);
        }
    }

    @Test public void noPlacementLeavesA4Bounds() {
        for (IdCardExportPreset preset : IdCardExportPreset.values()) {
            IdCardPageLayout layout = twoSideLayout(preset);
            for (IdCardPageLayout.Placement side : layout.getPlacements()) {
                assertTrue(side.getLeft() >= 0f);
                assertTrue(side.getTop() >= 0f);
                assertTrue(side.getRight() <= layout.getPageWidth());
                assertTrue(side.getBottom() <= layout.getPageHeight());
            }
        }
    }

    @Test public void portraitBitmapUsesFitWithoutCropping() {
        assertFitInside(900, 1400);
    }

    @Test public void landscapeBitmapUsesFitWithoutCropping() {
        assertFitInside(1400, 900);
    }

    @Test public void extremeAspectRatioFallsBackToContainedFit() {
        assertFitInside(4000, 200);
    }

    private static IdCardPageLayout twoSideLayout(IdCardExportPreset preset) {
        return IdCardPageLayoutCalculator.calculate(
                preset,
                Arrays.asList(IdCardSide.FRONT, IdCardSide.BACK)
        );
    }

    private static void assertFitInside(int width, int height) {
        IdCardPageLayout.Placement box = twoSideLayout(IdCardExportPreset.EASY_TO_READ)
                .getPlacements().get(0);
        ImagePlacementCalculator.ImageDrawPlan plan =
                ImagePlacementCalculator.calculateDrawPlan(
                        width,
                        height,
                        box.getLeft(),
                        box.getTop(),
                        box.getWidth(),
                        box.getHeight(),
                        ImagePlacementMode.FIT
                );
        ImagePlacementCalculator.PlacementRect source = plan.getSourceRect();
        ImagePlacementCalculator.PlacementRect destination = plan.getDestinationRect();

        assertEquals(0f, source.getLeft(), DELTA);
        assertEquals(0f, source.getTop(), DELTA);
        assertEquals(width, source.getRight(), DELTA);
        assertEquals(height, source.getBottom(), DELTA);
        assertTrue(destination.getLeft() >= box.getLeft() - DELTA);
        assertTrue(destination.getTop() >= box.getTop() - DELTA);
        assertTrue(destination.getRight() <= box.getRight() + DELTA);
        assertTrue(destination.getBottom() <= box.getBottom() + DELTA);
        assertEquals(width / (float) height, destination.getWidth() / destination.getHeight(), DELTA);
    }
}
