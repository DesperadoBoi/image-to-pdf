package com.desperadoboi.imagetopdf.ui.viewer;

import android.graphics.Typeface;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.desperadoboi.imagetopdf.R;
import com.desperadoboi.imagetopdf.document.spreadsheet.ColumnLabelFormatter;
import com.desperadoboi.imagetopdf.document.spreadsheet.SpreadsheetData;

import java.util.Collections;
import java.util.List;

final class SpreadsheetRowAdapter
        extends RecyclerView.Adapter<SpreadsheetRowAdapter.ViewHolder> {
    private SpreadsheetData data = new SpreadsheetData(Collections.emptyList(), false, ',');

    void submit(SpreadsheetData data) {
        int previousCount = getItemCount();
        this.data = data;
        if (previousCount > 0) notifyItemRangeRemoved(0, previousCount);
        int currentCount = getItemCount();
        if (currentCount > 0) notifyItemRangeInserted(0, currentCount);
    }

    int getRequiredWidth(ViewGroup parent) {
        int rowHeader = parent.getResources().getDimensionPixelSize(R.dimen.viewer_row_header_width);
        int cellWidth = parent.getResources().getDimensionPixelSize(R.dimen.viewer_cell_width);
        return rowHeader + Math.max(1, data.getColumnCount()) * cellWidth;
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
                rowHeader
                        ? R.dimen.viewer_row_header_width
                        : R.dimen.viewer_cell_width
        );
        cell.setLayoutParams(new LinearLayout.LayoutParams(
                width,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        int padding = row.getResources().getDimensionPixelSize(R.dimen.viewer_cell_padding);
        cell.setPadding(padding, 0, padding, 0);
        cell.setGravity(headerStyle || rowHeader ? Gravity.CENTER : Gravity.CENTER_VERTICAL);
        cell.setMaxLines(2);
        cell.setEllipsize(android.text.TextUtils.TruncateAt.END);
        cell.setText(value);
        cell.setTextIsSelectable(!headerStyle && !rowHeader);
        cell.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
        cell.setTextColor(com.google.android.material.color.MaterialColors.getColor(
                cell,
                com.google.android.material.R.attr.colorOnSurface
        ));
        cell.setBackgroundResource(headerStyle || rowHeader
                ? R.drawable.bg_viewer_grid_header
                : R.drawable.bg_viewer_grid_cell);
        if (headerStyle || rowHeader) cell.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
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

    static final class ViewHolder extends RecyclerView.ViewHolder {
        private final LinearLayout rowView;

        ViewHolder(LinearLayout rowView) {
            super(rowView);
            this.rowView = rowView;
        }
    }
}
