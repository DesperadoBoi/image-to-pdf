package com.desperadoboi.imagetopdf.ui.viewer;

import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.desperadoboi.imagetopdf.R;
import com.desperadoboi.imagetopdf.document.spreadsheet.ColumnLabelFormatter;
import com.desperadoboi.imagetopdf.document.spreadsheet.SpreadsheetData;

import java.util.Collections;
import java.util.List;

final class SpreadsheetRowAdapter
        extends RecyclerView.Adapter<SpreadsheetRowAdapter.ViewHolder> {
    private static final int WIDTH_SAMPLE_ROW_COUNT = 80;
    private static final int WIDTH_SAMPLE_CHARACTER_COUNT = 64;

    private SpreadsheetData data = new SpreadsheetData(Collections.emptyList(), false, ',');
    private int[] baseColumnWidths = new int[0];
    private int[] scaledColumnWidths = new int[0];
    private float scale = ZoomController.NORMAL_ZOOM;
    private float horizontalOffset;
    private int rowHeaderWidth;
    private int baseRowHeight;
    private int basePadding;
    private float baseTextSize;
    @Nullable private RecyclerView recyclerView;

    void submit(SpreadsheetData data, ViewGroup parent) {
        int previousCount = getItemCount();
        this.data = data;
        readBaseMetrics(parent);
        calculateColumnWidths(parent);
        updateScaledColumnWidths();
        if (previousCount > 0) notifyItemRangeRemoved(0, previousCount);
        int currentCount = getItemCount();
        if (currentCount > 0) notifyItemRangeInserted(0, currentCount);
    }

    void setScale(float scale) {
        float clampedScale = ZoomController.clampZoom(scale);
        if (Math.abs(this.scale - clampedScale) < 0.0001f) return;
        this.scale = clampedScale;
        updateScaledColumnWidths();
        updateAttachedRows(true);
        if (recyclerView != null) recyclerView.requestLayout();
    }

    void setHorizontalOffset(float horizontalOffset) {
        this.horizontalOffset = Math.max(0f, horizontalOffset);
        updateAttachedRows(false);
    }

    float getScale() {
        return scale;
    }

    int getRowHeaderWidth() {
        return rowHeaderWidth;
    }

    int getHeaderHeight() {
        return baseRowHeight;
    }

    int getScaledRowHeight() {
        return Math.max(1, Math.round(baseRowHeight * scale));
    }

    int getUnscaledSheetWidth() {
        int width = 0;
        for (int columnWidth : baseColumnWidths) width += columnWidth;
        return width;
    }

    int getScaledSheetWidth() {
        int width = 0;
        for (int columnWidth : scaledColumnWidths) width += columnWidth;
        return width;
    }

    int[] getScaledColumnWidths() {
        return scaledColumnWidths;
    }

    int getBasePadding() {
        return basePadding;
    }

    float getBaseTextSize() {
        return baseTextSize;
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        this.recyclerView = recyclerView;
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        if (this.recyclerView == recyclerView) this.recyclerView = null;
        super.onDetachedFromRecyclerView(recyclerView);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        FrameLayout row = new FrameLayout(parent.getContext());
        row.setClipChildren(true);
        row.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                getScaledRowHeight()
        ));

        LinearLayout cells = new LinearLayout(parent.getContext());
        cells.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(cells);

        TextView rowNumber = createTextView(parent, true);
        row.addView(rowNumber);
        return new ViewHolder(row, cells, rowNumber);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(position);
    }

    @Override
    public int getItemCount() {
        return data.getRows().size();
    }

    private void updateAttachedRows(boolean updateMetrics) {
        if (recyclerView == null) return;
        for (int index = 0; index < recyclerView.getChildCount(); index++) {
            RecyclerView.ViewHolder holder = recyclerView.getChildViewHolder(
                    recyclerView.getChildAt(index)
            );
            if (!(holder instanceof ViewHolder)) continue;
            ViewHolder rowHolder = (ViewHolder) holder;
            if (updateMetrics) rowHolder.applyMetrics();
            rowHolder.applyHorizontalOffset();
        }
    }

    private TextView createTextView(ViewGroup parent, boolean rowHeader) {
        TextView cell = new TextView(parent.getContext());
        cell.setGravity(rowHeader ? Gravity.CENTER : Gravity.CENTER_VERTICAL);
        cell.setMaxLines(rowHeader ? 1 : 2);
        cell.setEllipsize(android.text.TextUtils.TruncateAt.END);
        cell.setTextColor(ContextCompat.getColor(
                parent.getContext(),
                R.color.viewer_document_text
        ));
        cell.setBackgroundResource(rowHeader
                ? R.drawable.bg_viewer_grid_row_header
                : R.drawable.bg_viewer_grid_cell);
        if (rowHeader) {
            cell.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        } else {
            cell.setTextIsSelectable(true);
        }
        return cell;
    }

    private void readBaseMetrics(ViewGroup parent) {
        rowHeaderWidth = parent.getResources().getDimensionPixelSize(
                R.dimen.viewer_row_header_width
        );
        baseRowHeight = parent.getResources().getDimensionPixelSize(R.dimen.viewer_cell_height);
        basePadding = parent.getResources().getDimensionPixelSize(R.dimen.viewer_cell_padding);
        baseTextSize = 14f * parent.getResources().getDisplayMetrics().scaledDensity;
    }

    private void calculateColumnWidths(ViewGroup parent) {
        int columnCount = Math.max(1, data.getColumnCount());
        int minimumWidth = parent.getResources().getDimensionPixelSize(
                R.dimen.viewer_cell_min_width
        );
        int maximumWidth = parent.getResources().getDimensionPixelSize(
                R.dimen.viewer_cell_max_width
        );
        int horizontalPadding = basePadding * 2;
        TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTypeface(Typeface.DEFAULT);
        textPaint.setTextSize(baseTextSize);

        baseColumnWidths = new int[columnCount];
        int sampledRows = Math.min(WIDTH_SAMPLE_ROW_COUNT, data.getRows().size());
        for (int column = 0; column < columnCount; column++) {
            float measuredWidth = textPaint.measureText(ColumnLabelFormatter.format(column));
            for (int rowIndex = 0; rowIndex < sampledRows; rowIndex++) {
                List<String> row = data.getRows().get(rowIndex);
                if (column >= row.size()) continue;
                String value = row.get(column);
                int measuredCharacters = Math.min(
                        WIDTH_SAMPLE_CHARACTER_COUNT,
                        value.length()
                );
                measuredWidth = Math.max(
                        measuredWidth,
                        textPaint.measureText(value, 0, measuredCharacters)
                );
            }
            int desiredWidth = (int) Math.ceil(measuredWidth) + horizontalPadding;
            baseColumnWidths[column] = Math.max(
                    minimumWidth,
                    Math.min(maximumWidth, desiredWidth)
            );
        }
    }

    private void updateScaledColumnWidths() {
        scaledColumnWidths = new int[baseColumnWidths.length];
        for (int index = 0; index < baseColumnWidths.length; index++) {
            scaledColumnWidths[index] = Math.max(1, Math.round(baseColumnWidths[index] * scale));
        }
    }

    private int scaledPadding() {
        float paddingScale = Math.max(0.35f, Math.min(2f, scale));
        return Math.max(1, Math.round(basePadding * paddingScale));
    }

    private float scaledTextSize() {
        float textScale = Math.max(0.65f, Math.min(2f, scale));
        return baseTextSize * textScale;
    }

    final class ViewHolder extends RecyclerView.ViewHolder {
        private final FrameLayout rowView;
        private final LinearLayout cellContainer;
        private final TextView rowNumberView;

        ViewHolder(
                FrameLayout rowView,
                LinearLayout cellContainer,
                TextView rowNumberView
        ) {
            super(rowView);
            this.rowView = rowView;
            this.cellContainer = cellContainer;
            this.rowNumberView = rowNumberView;
        }

        void bind(int position) {
            ensureCellCount();
            List<String> row = data.getRows().get(position);
            rowNumberView.setText(String.valueOf(position + 1));
            for (int column = 0; column < scaledColumnWidths.length; column++) {
                TextView cell = (TextView) cellContainer.getChildAt(column);
                String value = column < row.size() ? row.get(column) : "";
                cell.setText(value);
                cell.setContentDescription(cell.getResources().getString(
                        R.string.viewer_cell_content_description,
                        position + 1,
                        ColumnLabelFormatter.format(column),
                        value
                ));
            }
            applyMetrics();
            applyHorizontalOffset();
        }

        void applyMetrics() {
            ViewGroup.LayoutParams rowParams = rowView.getLayoutParams();
            rowParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
            rowParams.height = getScaledRowHeight();
            rowView.setLayoutParams(rowParams);

            FrameLayout.LayoutParams contentParams = new FrameLayout.LayoutParams(
                    getScaledSheetWidth(),
                    getScaledRowHeight()
            );
            contentParams.leftMargin = rowHeaderWidth;
            cellContainer.setLayoutParams(contentParams);

            FrameLayout.LayoutParams rowNumberParams = new FrameLayout.LayoutParams(
                    rowHeaderWidth,
                    getScaledRowHeight()
            );
            rowNumberView.setLayoutParams(rowNumberParams);
            rowNumberView.setTextSize(TypedValue.COMPLEX_UNIT_PX, scaledTextSize());

            int padding = scaledPadding();
            for (int column = 0; column < scaledColumnWidths.length; column++) {
                TextView cell = (TextView) cellContainer.getChildAt(column);
                cell.setLayoutParams(new LinearLayout.LayoutParams(
                        scaledColumnWidths[column],
                        ViewGroup.LayoutParams.MATCH_PARENT
                ));
                cell.setPadding(padding, 0, padding, 0);
                cell.setTextSize(TypedValue.COMPLEX_UNIT_PX, scaledTextSize());
            }
        }

        void applyHorizontalOffset() {
            cellContainer.setTranslationX(-horizontalOffset);
        }

        private void ensureCellCount() {
            while (cellContainer.getChildCount() < scaledColumnWidths.length) {
                cellContainer.addView(createTextView(rowView, false));
            }
            while (cellContainer.getChildCount() > scaledColumnWidths.length) {
                cellContainer.removeViewAt(cellContainer.getChildCount() - 1);
            }
        }
    }
}
