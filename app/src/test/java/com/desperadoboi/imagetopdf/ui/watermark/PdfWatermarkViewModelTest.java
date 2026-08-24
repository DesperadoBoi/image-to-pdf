package com.desperadoboi.imagetopdf.ui.watermark;

import androidx.lifecycle.SavedStateHandle;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class PdfWatermarkViewModelTest {
    @Test public void restoresSourceOptionsPageTargetingAndRange() {
        SavedStateHandle state = readyState();
        state.set("pdf_watermark.current_page", 3);
        state.set("pdf_watermark.text", "АРХИВНАЯ КОПИЯ");
        state.set("pdf_watermark.style", WatermarkStyle.TILED.name());
        state.set("pdf_watermark.position", WatermarkPosition.BOTTOM_RIGHT.name());
        state.set("pdf_watermark.opacity", 0.55f);
        state.set("pdf_watermark.size", 72f);
        state.set("pdf_watermark.rotation", 30f);
        state.set("pdf_watermark.color", WatermarkColor.BLUE.name());
        state.set("pdf_watermark.page_selection", WatermarkPageSelection.RANGE.name());
        state.set("pdf_watermark.range", "1, 3-4, 3");

        PdfWatermarkViewModel viewModel = new PdfWatermarkViewModel(state);

        assertEquals("viewer_550e8400-e29b-41d4-a716-446655440000.cache",
                viewModel.getSourceCacheFileName());
        assertEquals(3, viewModel.getCurrentPage());
        assertEquals("АРХИВНАЯ КОПИЯ", viewModel.getOptions().getText());
        assertEquals(WatermarkStyle.TILED, viewModel.getOptions().getStyle());
        assertEquals(WatermarkPosition.BOTTOM_RIGHT, viewModel.getOptions().getPosition());
        assertEquals(0.55f, viewModel.getOptions().getOpacity(), 0f);
        assertEquals(72f, viewModel.getOptions().getSizePoints(), 0f);
        assertEquals(30f, viewModel.getOptions().getRotationDegrees(), 0f);
        assertEquals(WatermarkColor.BLUE, viewModel.getOptions().getColor());
        assertEquals("1,3-4", viewModel.getRangeResult().normalized());
        assertEquals(3, viewModel.getTargetPages().size());
        assertTrue(viewModel.canCreatePdf());
    }

    @Test public void invalidRangeAndBlankTextBlockCreation() {
        PdfWatermarkViewModel viewModel = new PdfWatermarkViewModel(readyState());
        viewModel.setPageSelection(WatermarkPageSelection.RANGE);
        viewModel.setRangeInput("0,4-2");
        assertFalse(viewModel.canCreatePdf());

        viewModel.setRangeInput("1-2");
        viewModel.setText("   ");
        assertFalse(viewModel.canCreatePdf());
    }

    @Test public void rotationDoesNotStartSecondExport() {
        SavedStateHandle state = readyState();
        PdfWatermarkViewModel viewModel = new PdfWatermarkViewModel(state);
        PdfWatermarkViewModel.ExportOperation first = viewModel.startExport();

        assertNotNull(first);
        assertEquals(null, viewModel.startExport());

        PdfWatermarkViewModel restored = new PdfWatermarkViewModel(state);
        assertFalse(restored.getExportState().isBusy());
        assertEquals(PdfWatermarkError.CANCELLED, restored.getExportState().getError());
    }

    @Test public void restoredGeneratingExportBecomesInterruptedError() {
        SavedStateHandle state = readyState();
        state.set("pdf_watermark.export_phase", PdfWatermarkExportState.Phase.GENERATING.name());
        state.set("pdf_watermark.export_operation", 18L);
        state.set("pdf_watermark.export_total", 12);

        PdfWatermarkViewModel restored = new PdfWatermarkViewModel(state);

        assertEquals(PdfWatermarkExportState.Phase.ERROR,
                restored.getExportState().getPhase());
        assertEquals(PdfWatermarkError.CANCELLED, restored.getExportState().getError());
    }

    private static SavedStateHandle readyState() {
        SavedStateHandle state = new SavedStateHandle();
        state.set("pdf_watermark.source_file",
                "viewer_550e8400-e29b-41d4-a716-446655440000.cache");
        state.set("pdf_watermark.source_name", "Sample.pdf");
        state.set("pdf_watermark.page_count", 12);
        state.set("pdf_watermark.source_status",
                PdfWatermarkViewModel.SourceStatus.READY.name());
        state.set("pdf_watermark.source_error", PdfWatermarkError.NONE.name());
        state.set("pdf_watermark.text", "COPY");
        return state;
    }

}
