package com.desperadoboi.imagetopdf.ui.viewer;

import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
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
    private int[] columnWidths = new int[0];

    void submit(SpreadsheetData data) {
        int previousCount = getItemCount();
        this.data = data;
        columnWidths = new int[0];
        if (previousCount > 0) notifyItemRangeRemoved(0, previousCount);
        int currentCount = getItemCount();
        if (currentCount > 0) notifyItemRangeInserted(0, currentCount);
    }

    int getRequiredWidth(ViewGroup parent) {
        calculateColumnWidths(parent);
        int rowHeader = parent.getResources().getDimensionPixelSize(R.dimen.viewer_row_header_width);
        int requiredWidth = rowHeader;
        for (int columnWidth : columnWidths) requiredWidth += columnWidth;
        return requiredWidth;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LinearLayout row = new LinearLayout(parent.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                parent.getResources().getDimensionPixelSize(R.dimen.viewer_cell_height)
        ));
        return new ViewHolder(row);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LinearLayout rowView = holder.rowView;
        rowView.removeAllViews();
        boolean header = position == 0;
        addCell(rowView, header ? "" : String.valueOf(position), true, true, -1, position);
        int columnCount = Math.max(1, data.getColumnCount());
        List<String> row = header
                ? Collections.emptyList()
                : data.getRows().get(position - 1);
        for (int column = 0; column < columnCount; column++) {
            String value = header
                    ? ColumnLabelFormatter.format(column)
                    : column < row.size() ? row.get(column) : "";
            addCell(rowView, value, false, header, column, position);
        }
    }

    @Override
    public int getItemCount() {
        return data.getRows().isEmpty() ? 0 : data.getRows().size() + 1;
    }

    private void addCell(
            LinearLayout row,
            String value,
            boolean rowHeader,
            boolean headerStyle,
            int column,
            int adapterPosition
    ) {
        TextView cell = new TextView(row.getContext());
        int width = row.getResources().getDimensionPixelSize(
                R.dimen.viewer_row_header_width
        );
        if (!rowHeader) {
            width = column < columnWidths.length
                    ? columnWidths[column]
                    : row.getResources().getDimensionPixelSize(R.dimen.viewer_cell_min_width);
        }
        cell.setLayoutParams(new LinearLayout.LayoutParams(
                width,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        int padding = row.getResources().getDimensionPixelSize(R.dimen.viewer_cell_padding);
        cell.setPadding(padding, 0, padding, 0);
        cell.setGravity(headerStyle || rowHeader ? Gravity.CENTER : Gravity.CENTER_VERTICAL);
        cell.setMaxLines(headerStyle || rowHeader ? 1 : 2);
        cell.setEllipsize(android.text.TextUtils.TruncateAt.END);
        cell.setText(value);
        cell.setTextIsSelectable(!headerStyle && !rowHeader);
        cell.setTextAppearance(headerStyle || rowHeader
                ? com.google.android.material.R.style.TextAppearance_Material3_LabelMedium
                : com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        cell.setTextColor(ContextCompat.getColor(row.getContext(), R.color.viewer_document_text));
        cell.setBackgroundResource(backgroundFor(rowHeader, headerStyle));
        if (headerStyle || rowHeader) {
            cell.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
        if (adapterPosition > 0 && !rowHeader) {
            cell.setContentDescription(row.getResources().getString(
                    R.string.viewer_cell_content_description,
                    adapterPosition,
                    ColumnLabelFormatter.format(column),
                    value
            ));
        }
        row.addView(cell);
    }

    private int backgroundFor(boolean rowHeader, boolean headerStyle) {
        if (rowHeader && headerStyle) return R.drawable.bg_viewer_grid_corner_header;
        if (headerStyle) return R.drawable.bg_viewer_grid_header;
        if (rowHeader) return R.drawable.bg_viewer_grid_row_header;
        return R.drawable.bg_viewer_grid_cell;
    }

    private void calculateColumnWidths(ViewGroup parent) {
        int columnCount = Math.max(1, data.getColumnCount());
        if (columnWidths.length == columnCount) return;

        int minimumWidth = parent.getResources().getDimensionPixelSize(
                R.dimen.viewer_cell_min_width
        );
        int maximumWidth = parent.getResources().getDimensionPixelSize(
                R.dimen.viewer_cell_max_width
        );
        int horizontalPadding = parent.getResources().getDimensionPixelSize(
                R.dimen.viewer_cell_padding
        ) * 2;
        TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTypeface(Typeface.DEFAULT);
        textPaint.setTextSize(14f * parent.getResources().getDisplayMetrics().scaledDensity);

        columnWidths = new int[columnCount];
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
            columnWidths[column] = Math.max(
                    minimumWidth,
                    Math.min(maximumWidth, desiredWidth)
            );
        }
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        private final LinearLayout rowView;

        ViewHolder(LinearLayout rowView) {
            super(rowView);
            this.rowView = rowView;
        }
    }
}
