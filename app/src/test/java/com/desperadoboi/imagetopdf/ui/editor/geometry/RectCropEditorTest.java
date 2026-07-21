package com.desperadoboi.imagetopdf.ui.editor.geometry;

import com.desperadoboi.imagetopdf.model.CropRect;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

public class RectCropEditorTest {
    private static final float MINIMUM = 0.1f;

    @Test
    public void movesTopLeftCorner() {
        assertEquals(
                rect(0.2f, 0.3f, 1f, 1f),
                resize(CropRect.FULL, RectCropEditor.Handle.TOP_LEFT, 0.2f, 0.3f)
        );
    }

    @Test
    public void movesTopRightCorner() {
        assertEquals(
                rect(0f, 0.2f, 0.8f, 1f),
                resize(CropRect.FULL, RectCropEditor.Handle.TOP_RIGHT, 0.8f, 0.2f)
        );
    }

    @Test
    public void movesBottomRightCorner() {
        assertEquals(
                rect(0f, 0f, 0.8f, 0.7f),
                resize(CropRect.FULL, RectCropEditor.Handle.BOTTOM_RIGHT, 0.8f, 0.7f)
        );
    }

    @Test
    public void movesBottomLeftCorner() {
        assertEquals(
                rect(0.2f, 0f, 1f, 0.7f),
                resize(CropRect.FULL, RectCropEditor.Handle.BOTTOM_LEFT, 0.2f, 0.7f)
        );
    }

    @Test
    public void topCenterMovesOnlyTop() {
        assertEquals(
                rect(0f, 0.25f, 1f, 1f),
                resize(CropRect.FULL, RectCropEditor.Handle.TOP_CENTER, 0.5f, 0.25f)
        );
    }

    @Test
    public void centerRightMovesOnlyRight() {
        assertEquals(
                rect(0f, 0f, 0.75f, 1f),
                resize(CropRect.FULL, RectCropEditor.Handle.CENTER_RIGHT, 0.75f, 0.5f)
        );
    }

    @Test
    public void bottomCenterMovesOnlyBottom() {
        assertEquals(
                rect(0f, 0f, 1f, 0.75f),
                resize(CropRect.FULL, RectCropEditor.Handle.BOTTOM_CENTER, 0.5f, 0.75f)
        );
    }

    @Test
    public void centerLeftMovesOnlyLeft() {
        assertEquals(
                rect(0.25f, 0f, 1f, 1f),
                resize(CropRect.FULL, RectCropEditor.Handle.CENTER_LEFT, 0.25f, 0.5f)
        );
    }

    @Test
    public void movingWholeCropPreservesSize() {
        CropRect original = rect(0.2f, 0.3f, 0.6f, 0.8f);

        CropRect moved = RectCropEditor.move(original, 0.15f, -0.2f);

        assertEquals(rect(0.35f, 0.1f, 0.75f, 0.6f), moved);
        assertEquals(original.getWidth(), moved.getWidth(), 0.0001f);
        assertEquals(original.getHeight(), moved.getHeight(), 0.0001f);
    }

    @Test
    public void movingWholeCropClampsToImageBounds() {
        CropRect original = rect(0.2f, 0.3f, 0.6f, 0.8f);

        assertEquals(rect(0.6f, 0.5f, 1f, 1f), RectCropEditor.move(original, 1f, 1f));
        assertEquals(rect(0f, 0f, 0.4f, 0.5f), RectCropEditor.move(original, -1f, -1f));
    }

    @Test
    public void resizeClampsToBoundsAndMinimumSizeWithoutInversion() {
        CropRect original = rect(0.2f, 0.2f, 0.8f, 0.8f);

        assertEquals(
                rect(0.55f, 0.2f, 0.8f, 0.8f),
                RectCropEditor.moveHandle(
                        original,
                        RectCropEditor.Handle.CENTER_LEFT,
                        2f,
                        0.5f,
                        0.25f,
                        0.25f
                )
        );
        assertEquals(
                rect(0.2f, 0.2f, 0.8f, 0.45f),
                RectCropEditor.moveHandle(
                        original,
                        RectCropEditor.Handle.BOTTOM_CENTER,
                        0.5f,
                        -1f,
                        0.25f,
                        0.25f
                )
        );
    }

    @Test
    public void nonFiniteMovementKeepsCropAndInvalidMinimumIsRejected() {
        CropRect original = rect(0.2f, 0.2f, 0.8f, 0.8f);

        assertSame(original, RectCropEditor.move(original, Float.NaN, 0f));
        assertSame(
                original,
                RectCropEditor.moveHandle(
                        original,
                        RectCropEditor.Handle.TOP_LEFT,
                        Float.NaN,
                        0f,
                        MINIMUM,
                        MINIMUM
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> RectCropEditor.moveHandle(
                        original,
                        RectCropEditor.Handle.TOP_LEFT,
                        0f,
                        0f,
                        0f,
                        MINIMUM
                )
        );
    }

    private CropRect resize(
            CropRect cropRect,
            RectCropEditor.Handle handle,
            float targetX,
            float targetY
    ) {
        return RectCropEditor.moveHandle(
                cropRect,
                handle,
                targetX,
                targetY,
                MINIMUM,
                MINIMUM
        );
    }

    private CropRect rect(float left, float top, float right, float bottom) {
        return new CropRect(left, top, right, bottom);
    }
}
