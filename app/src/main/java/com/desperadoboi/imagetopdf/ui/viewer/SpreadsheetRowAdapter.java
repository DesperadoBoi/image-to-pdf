package com.desperadoboi.imagetopdf.ui.viewer;

import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
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
import com.desperadoboi.imagetopdf.document.spreadsheet.SpreadsheetCellStyle;
import com.desperadoboi.imagetopdf.document.spreadsheet.SpreadsheetData;
import com.desperadoboi.imagetopdf.document.spreadsheet.SpreadsheetMergedRange;
import com.desperadoboi.imagetopdf.document.spreadsheet.SpreadsheetSheetLayout;

import java.util.Collections;
import java.util.List;

final class SpreadsheetRowAdapter
        extends RecyclerView.Adapter<SpreadsheetRowAdapter.ViewHolder> {
    private static final int WIDTH_SAMPLE_ROW_COUNT = 80;
    private static final int WIDTH_SAMPLE_CHARACTER_COUNT = 64;
    private static final float EXCEL_CHARACTER_WIDTH_DP = 7f;
    private static final float EXCEL_COLUMN_PADDING_DP = 5f;
    private static final float POINTS_TO_DP = 4f / 3f;

    private SpreadsheetData data = new SpreadsheetData(Collections.emptyList(), false, ',');
    @Nullable private SpreadsheetSheetLayout sheetLayout;
    private int[] baseColumnWidths = new int[0];
    private int[] scaledColumnWidths = new int[0];
    private int[] baseRowHeights = new int[0];
    private int[] scaledRowHeights = new int[0];
    private int[] baseRowOffsets = new int[]{0};
    private int[] scaledRowOffsets = new int[]{0};
    private SpreadsheetMergedRange[][] mergedRangesByRow = new SpreadsheetMergedRange[0][];
    private RowTextMetrics[] rowTextMetrics = new RowTextMetrics[0];
    private final TextPaint measurementPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private float scale = ZoomController.NORMAL_ZOOM;
    private float horizontalOffset;
    private int rowHeaderWidth;
    private int headerHeight;
    private int baseCsvRowHeight;
    private int minimumManualRowHeight;
    private int basePadding;
    private float baseTextSize;
    private float minimumManualTextSize;
    private int defaultCellColor;
    private int defaultTextColor;
    private int gridColor;
    private float density;
    private float scaledDensity;
    private ZoomController.ZoomMode zoomMode = ZoomController.ZoomMode.ZOOM_100;
    @Nullable private RecyclerView recyclerView;

    void submit(
            SpreadsheetData data,
            @Nullable SpreadsheetSheetLayout sheetLayout,
            ViewGroup parent
    ) {
        int previousCount = getItemCount();
        this.data = data;
        this.sheetLayout = sheetLayout;
        readBaseMetrics(parent);
        calculateColumnWidths(parent);
        calculateRowHeights();
        indexMergedRanges();
        calculateRowTextMetrics();
        updateScaledMetrics();
        if (previousCount > 0) notifyItemRangeRemoved(0, previousCount);
        int currentCount = getItemCount();
        if (currentCount > 0) notifyItemRangeInserted(0, currentCount);
    }

    void setZoom(float scale, ZoomController.ZoomMode zoomMode) {
        float clampedScale = ZoomController.clampZoom(scale, zoomMode);
        if (Math.abs(this.scale - clampedScale) < 0.0001f
                && this.zoomMode == zoomMode) {
            return;
        }
        this.scale = clampedScale;
        this.zoomMode = zoomMode;
        updateScaledMetrics();
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
        return headerHeight;
    }

    int getScaledRowHeight(int row) {
        if (scaledRowHeights.length == 0) return Math.max(1, baseCsvRowHeight);
        int safeRow = Math.max(0, Math.min(row, scaledRowHeights.length - 1));
        return scaledRowHeights[safeRow];
    }

    int getUnscaledSheetWidth() {
        return sum(baseColumnWidths);
    }

    int getScaledSheetWidth() {
        return sum(scaledColumnWidths);
    }

    int getUnscaledSheetHeight() {
        return baseRowOffsets[baseRowOffsets.length - 1];
    }

    int getScaledSheetHeight() {
        return scaledRowOffsets[scaledRowOffsets.length - 1];
    }

    int[] getScaledColumnWidths() {
        return scaledColumnWidths;
    }

    int getScaledPadding() {
        return SpreadsheetRenderMetrics.scaledPadding(basePadding, scale, zoomMode);
    }

    float getScaledTextSize() {
        return getScaledTextSize(SpreadsheetCellStyle.DEFAULT);
    }

    float getScaledTextSize(SpreadsheetCellStyle style) {
        return SpreadsheetRenderMetrics.effectiveFontSize(
                getBaseTextSize(style),
                scale,
                zoomMode,
                minimumManualTextSize
        );
    }

    float scaledOffsetToBase(float scaledOffset) {
        if (scaledRowHeights.length == 0) return 0f;
        int row = rowForOffset(scaledRowOffsets, scaledOffset);
        float within = Math.max(0f, scaledOffset - scaledRowOffsets[row]);
        float fraction = Math.min(1f, within / Math.max(1f, scaledRowHeights[row]));
        return baseRowOffsets[row] + fraction * baseRowHeights[row];
    }

    float baseOffsetToScaled(float baseOffset) {
        if (baseRowHeights.length == 0) return 0f;
        int row = rowForOffset(baseRowOffsets, baseOffset);
        float within = Math.max(0f, baseOffset - baseRowOffsets[row]);
        float fraction = Math.min(1f, within / Math.max(1f, baseRowHeights[row]));
        return scaledRowOffsets[row] + fraction * scaledRowHeights[row];
    }

    int rowForScaledOffset(float offset) {
        return rowForOffset(scaledRowOffsets, offset);
    }

    int getScaledRowOffset(int row) {
        int safeRow = Math.max(0, Math.min(row, scaledRowHeights.length));
        return scaledRowOffsets[safeRow];
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
        row.setClipChildren(false);
        row.setClipToPadding(false);
        row.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                getScaledRowHeight(0)
        ));

        LinearLayout cells = new LinearLayout(parent.getContext());
        cells.setOrientation(LinearLayout.HORIZONTAL);
        cells.setClipChildren(false);
        cells.setClipToPadding(false);
        row.addView(cells);

        TextView rowNumber = createRowHeader(parent);
        row.addView(rowNumber);
        return new ViewHolder(row, cells, rowNumber);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(position);
    }

    @Override
    public int getItemCount() {
        return sheetLayout == null
                ? data.getRows().size()
                : Math.max(data.getRows().size(), sheetLayout.getRowCount());
    }

    private void updateAttachedRows(boolean updateMetrics) {
        if (recyclerView == null) return;
        for (int index = 0; index < recyclerView.getChildCount(); index++) {
            RecyclerView.ViewHolder holder = recyclerView.getChildViewHolder(
                    recyclerView.getChildAt(index)
            );
            if (!(holder instanceof ViewHolder)) continue;
            ViewHolder rowHolder = (ViewHolder) holder;
            if (updateMetrics) rowHolder.bind(rowHolder.getBindingAdapterPosition());
            else rowHolder.applyHorizontalOffset();
        }
    }

    private TextView createRowHeader(ViewGroup parent) {
        TextView cell = new TextView(parent.getContext());
        cell.setGravity(Gravity.CENTER);
        cell.setTextColor(defaultTextColor);
        cell.setIncludeFontPadding(false);
        cell.setLineSpacing(0f, 1f);
        cell.setMinLines(0);
        cell.setMaxLines(1);
        cell.setEllipsize(TextUtils.TruncateAt.END);
        cell.setBackgroundResource(R.drawable.bg_viewer_grid_row_header);
        cell.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return cell;
    }

    private void readBaseMetrics(ViewGroup parent) {
        rowHeaderWidth = parent.getResources().getDimensionPixelSize(
                R.dimen.viewer_row_header_width
        );
        baseCsvRowHeight = parent.getResources().getDimensionPixelSize(
                R.dimen.viewer_cell_height
        );
        headerHeight = parent.getResources().getDimensionPixelSize(
                R.dimen.viewer_spreadsheet_header_height
        );
        minimumManualRowHeight = parent.getResources().getDimensionPixelSize(
                R.dimen.viewer_cell_manual_min_height
        );
        basePadding = parent.getResources().getDimensionPixelSize(R.dimen.viewer_cell_padding);
        baseTextSize = parent.getResources().getDimension(R.dimen.viewer_cell_text_size);
        minimumManualTextSize = parent.getResources().getDimension(
                R.dimen.viewer_cell_manual_min_text_size
        );
        defaultCellColor = ContextCompat.getColor(
                parent.getContext(),
                R.color.viewer_document_surface
        );
        defaultTextColor = ContextCompat.getColor(
                parent.getContext(),
                R.color.viewer_document_text
        );
        gridColor = ContextCompat.getColor(parent.getContext(), R.color.viewer_table_grid);
        density = parent.getResources().getDisplayMetrics().density;
        scaledDensity = parent.getResources().getDisplayMetrics().scaledDensity;
    }

    private void calculateColumnWidths(ViewGroup parent) {
        int columnCount = Math.max(
                1,
                sheetLayout == null
                        ? data.getColumnCount()
                        : Math.max(data.getColumnCount(), sheetLayout.getColumnCount())
        );
        baseColumnWidths = new int[columnCount];
        if (sheetLayout != null) {
            int minimumWidth = Math.max(1, Math.round(4f * density));
            int maximumWidth = Math.round(480f * density);
            for (int column = 0; column < columnCount; column++) {
                float widthDp = sheetLayout.getColumnWidthCharacters(column)
                        * EXCEL_CHARACTER_WIDTH_DP
                        + EXCEL_COLUMN_PADDING_DP;
                baseColumnWidths[column] = Math.max(
                        minimumWidth,
                        Math.min(maximumWidth, Math.round(widthDp * density))
                );
            }
            return;
        }

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
        int sampledRows = Math.min(WIDTH_SAMPLE_ROW_COUNT, data.getRows().size());
        for (int column = 0; column < columnCount; column++) {
            float measuredWidth = textPaint.measureText(ColumnLabelFormatter.format(column));
            for (int rowIndex = 0; rowIndex < sampledRows; rowIndex++) {
                List<String> row = data.getRows().get(rowIndex);
                if (column >= row.size()) continue;
                String value = row.get(column);
                int measuredCharacters = Math.min(WIDTH_SAMPLE_CHARACTER_COUNT, value.length());
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

    private void calculateRowHeights() {
        int rowCount = getItemCount();
        baseRowHeights = new int[rowCount];
        for (int row = 0; row < rowCount; row++) {
            if (sheetLayout == null) {
                baseRowHeights[row] = baseCsvRowHeight;
            } else {
                float heightDp = sheetLayout.getRowHeightPoints(row) * POINTS_TO_DP;
                baseRowHeights[row] = heightDp <= 0f
                        ? 0
                        : Math.max(1, Math.round(heightDp * density));
            }
        }
        baseRowOffsets = offsets(baseRowHeights);
    }

    private void updateScaledMetrics() {
        scaledColumnWidths = new int[baseColumnWidths.length];
        for (int index = 0; index < baseColumnWidths.length; index++) {
            scaledColumnWidths[index] = Math.max(
                    1,
                    Math.round(baseColumnWidths[index] * scale)
            );
        }
        scaledRowHeights = new int[baseRowHeights.length];
        for (int row = 0; row < baseRowHeights.length; row++) {
            boolean hidden = baseRowHeights[row] == 0;
            float textHeight = ZoomController.isOverview(zoomMode)
                    ? 0f
                    : measureManualTextHeight(row);
            scaledRowHeights[row] = SpreadsheetRenderMetrics.rowHeight(
                    Math.max(1, baseRowHeights[row]),
                    scale,
                    zoomMode,
                    hidden,
                    textHeight,
                    Math.max(0, getScaledPadding() / 3),
                    minimumManualRowHeight
            );
        }
        scaledRowOffsets = offsets(scaledRowHeights);
    }

    private void indexMergedRanges() {
        mergedRangesByRow = new SpreadsheetMergedRange[getItemCount()][];
        if (sheetLayout == null) return;
        for (SpreadsheetMergedRange range : sheetLayout.getMergedRanges()) {
            int lastRow = Math.min(range.getLastRow(), mergedRangesByRow.length - 1);
            int lastColumn = Math.min(range.getLastColumn(), baseColumnWidths.length - 1);
            for (int row = Math.max(0, range.getFirstRow()); row <= lastRow; row++) {
                if (mergedRangesByRow[row] == null) {
                    mergedRangesByRow[row] =
                            new SpreadsheetMergedRange[baseColumnWidths.length];
                }
                for (int column = Math.max(0, range.getFirstColumn());
                        column <= lastColumn;
                        column++) {
                    mergedRangesByRow[row][column] = range;
                }
            }
        }
    }

    @Nullable
    private SpreadsheetMergedRange mergedRangeAt(int row, int column) {
        if (row < 0 || row >= mergedRangesByRow.length
                || mergedRangesByRow[row] == null
                || column < 0 || column >= mergedRangesByRow[row].length) {
            return null;
        }
        return mergedRangesByRow[row][column];
    }

    private void calculateRowTextMetrics() {
        rowTextMetrics = new RowTextMetrics[getItemCount()];
        for (int rowIndex = 0; rowIndex < rowTextMetrics.length; rowIndex++) {
            if (rowIndex >= data.getRows().size()) {
                rowTextMetrics[rowIndex] = RowTextMetrics.EMPTY;
                continue;
            }
            List<String> row = data.getRows().get(rowIndex);
            SpreadsheetCellStyle tallestSingleLineStyle = null;
            float tallestSingleLineSize = 0f;
            List<WrappedCell> wrappedCells = null;
            for (int column = 0;
                    column < row.size() && column < baseColumnWidths.length;
                    column++) {
                String value = row.get(column);
                if (value == null || value.isEmpty()) continue;
                SpreadsheetMergedRange mergedRange = mergedRangeAt(rowIndex, column);
                if (mergedRange != null && !mergedRange.isAnchor(rowIndex, column)) continue;
                if (mergedRange != null && mergedRange.getLastRow() > mergedRange.getFirstRow()) {
                    continue;
                }
                SpreadsheetCellStyle style = sheetLayout == null
                        ? SpreadsheetCellStyle.DEFAULT
                        : sheetLayout.getCellStyle(rowIndex, column);
                if (style.isWrapText()) {
                    if (wrappedCells == null) wrappedCells = new java.util.ArrayList<>();
                    wrappedCells.add(new WrappedCell(value, column, style, mergedRange));
                } else {
                    float textSize = getBaseTextSize(style);
                    if (textSize > tallestSingleLineSize) {
                        tallestSingleLineSize = textSize;
                        tallestSingleLineStyle = style;
                    }
                }
            }
            rowTextMetrics[rowIndex] = new RowTextMetrics(
                    tallestSingleLineStyle,
                    wrappedCells == null ? Collections.emptyList() : wrappedCells
            );
        }
    }

    private int mergedWidth(SpreadsheetMergedRange range) {
        int width = 0;
        for (int column = range.getFirstColumn();
                column <= range.getLastColumn() && column < scaledColumnWidths.length;
                column++) {
            width += scaledColumnWidths[column];
        }
        return width;
    }

    private int mergedHeight(SpreadsheetMergedRange range) {
        int lastRow = Math.min(range.getLastRow(), scaledRowHeights.length - 1);
        return scaledRowOffsets[lastRow + 1] - scaledRowOffsets[range.getFirstRow()];
    }

    private float measureManualTextHeight(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= rowTextMetrics.length) return 0f;
        RowTextMetrics rowMetrics = rowTextMetrics[rowIndex];
        float maximumHeight = 0f;
        if (rowMetrics.tallestSingleLineStyle != null) {
            measurementPaint.setTypeface(typefaceFor(rowMetrics.tallestSingleLineStyle));
            measurementPaint.setTextSize(getScaledTextSize(rowMetrics.tallestSingleLineStyle));
            Paint.FontMetrics metrics = measurementPaint.getFontMetrics();
            maximumHeight = SpreadsheetRenderMetrics.lineHeight(
                    metrics.ascent,
                    metrics.descent,
                    metrics.leading
            );
        }
        for (WrappedCell cell : rowMetrics.wrappedCells) {
            measurementPaint.setTypeface(typefaceFor(cell.style));
            measurementPaint.setTextSize(getScaledTextSize(cell.style));
            Paint.FontMetrics metrics = measurementPaint.getFontMetrics();
            float lineHeight = SpreadsheetRenderMetrics.lineHeight(
                    metrics.ascent,
                    metrics.descent,
                    metrics.leading
            );
            int width = cell.mergedRange == null
                    ? scaledColumnWidths[cell.column]
                    : mergedWidth(cell.mergedRange);
            float availableWidth = Math.max(0f, width - getScaledPadding() * 2f);
            int lines = SpreadsheetCellView.countWrappedLines(
                    cell.value,
                    measurementPaint,
                    availableWidth
            );
            maximumHeight = Math.max(maximumHeight, lineHeight * Math.max(1, lines));
        }
        return maximumHeight;
    }

    private float getBaseTextSize(SpreadsheetCellStyle style) {
        return style.getFontSizePoints() > 0f
                ? style.getFontSizePoints() * scaledDensity
                : baseTextSize;
    }

    private Typeface typefaceFor(SpreadsheetCellStyle style) {
        return Typeface.create(
                "sans-serif",
                style.isBold() && style.isItalic()
                        ? Typeface.BOLD_ITALIC
                        : style.isBold()
                                ? Typeface.BOLD
                                : style.isItalic() ? Typeface.ITALIC : Typeface.NORMAL
        );
    }

    private int gravityFor(SpreadsheetCellStyle style, String value) {
        int horizontal;
        switch (style.getHorizontalAlignment()) {
            case CENTER:
                horizontal = Gravity.CENTER_HORIZONTAL;
                break;
            case RIGHT:
                horizontal = Gravity.END;
                break;
            case JUSTIFY:
                horizontal = Gravity.FILL_HORIZONTAL;
                break;
            case GENERAL:
                horizontal = looksNumeric(value) ? Gravity.END : Gravity.START;
                break;
            case LEFT:
            default:
                horizontal = Gravity.START;
                break;
        }
        int vertical;
        switch (style.getVerticalAlignment()) {
            case TOP:
                vertical = Gravity.TOP;
                break;
            case CENTER:
                vertical = Gravity.CENTER_VERTICAL;
                break;
            case BOTTOM:
            default:
                vertical = Gravity.BOTTOM;
                break;
        }
        return horizontal | vertical;
    }

    private boolean looksNumeric(String value) {
        if (value == null || value.isEmpty()) return false;
        int index = value.charAt(0) == '-' || value.charAt(0) == '+' ? 1 : 0;
        boolean digit = false;
        for (; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isDigit(current)) digit = true;
            else if (current != '.' && current != ',' && current != ' ') return false;
        }
        return digit;
    }

    private int[] offsets(int[] sizes) {
        int[] result = new int[sizes.length + 1];
        for (int index = 0; index < sizes.length; index++) {
            long next = (long) result[index] + sizes[index];
            result[index + 1] = next > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) next;
        }
        return result;
    }

    private int rowForOffset(int[] offsets, float offset) {
        if (offsets.length <= 1) return 0;
        float clamped = Math.max(0f, Math.min(offset, offsets[offsets.length - 1] - 1f));
        int low = 0;
        int high = offsets.length - 2;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            if (clamped < offsets[middle]) {
                high = middle - 1;
            } else if (clamped >= offsets[middle + 1]) {
                low = middle + 1;
            } else {
                return middle;
            }
        }
        return Math.max(0, Math.min(low, offsets.length - 2));
    }

    private int sum(int[] values) {
        long total = 0L;
        for (int value : values) total += value;
        return total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }

    final class ViewHolder extends RecyclerView.ViewHolder {
        private final FrameLayout rowView;
        private final LinearLayout cellContainer;
        private final TextView rowNumberView;
        private int boundPosition = RecyclerView.NO_POSITION;

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
            if (position == RecyclerView.NO_POSITION || position >= getItemCount()) return;
            boundPosition = position;
            ensureCellCount();
            List<String> row = position < data.getRows().size()
                    ? data.getRows().get(position)
                    : Collections.emptyList();
            boolean anchorsVerticalMerge = false;
            rowNumberView.setText(String.valueOf(position + 1));
            for (int column = 0; column < scaledColumnWidths.length; column++) {
                SpreadsheetCellView cell =
                        (SpreadsheetCellView) cellContainer.getChildAt(column);
                SpreadsheetMergedRange mergedRange = mergedRangeAt(position, column);
                boolean anchor = mergedRange != null
                        && mergedRange.isAnchor(position, column);
                boolean covered = mergedRange != null && !anchor;
                String value = column < row.size() ? row.get(column) : "";
                if (covered) value = "";
                SpreadsheetCellStyle style = sheetLayout == null
                        ? SpreadsheetCellStyle.DEFAULT
                        : sheetLayout.getCellStyle(
                                anchor ? mergedRange.getFirstRow() : position,
                                anchor ? mergedRange.getFirstColumn() : column
                        );
                applyCellPresentation(cell, style, value);
                cell.setText(value);
                cell.setVisibility(covered ? View.INVISIBLE : View.VISIBLE);
                cell.setContentDescription(cell.getResources().getString(
                        R.string.viewer_cell_content_description,
                        position + 1,
                        ColumnLabelFormatter.format(column),
                        value
                ));

                int width = scaledColumnWidths[column];
                int height = getScaledRowHeight(position);
                if (anchor) {
                    width = mergedWidth(mergedRange);
                    height = mergedHeight(mergedRange);
                    anchorsVerticalMerge |= mergedRange.getLastRow() > mergedRange.getFirstRow();
                } else if (mergedRange != null
                        && position == mergedRange.getFirstRow()
                        && column > mergedRange.getFirstColumn()) {
                    width = 0;
                }
                cell.setLayoutParams(new LinearLayout.LayoutParams(width, height));
            }
            rowView.setTranslationZ(anchorsVerticalMerge ? density : 0f);
            applyMetrics();
            applyHorizontalOffset();
        }

        void applyMetrics() {
            if (boundPosition == RecyclerView.NO_POSITION) return;
            int rowHeight = getScaledRowHeight(boundPosition);
            ViewGroup.LayoutParams rowParams = rowView.getLayoutParams();
            rowParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
            rowParams.height = rowHeight;
            rowView.setLayoutParams(rowParams);

            FrameLayout.LayoutParams contentParams = new FrameLayout.LayoutParams(
                    getScaledSheetWidth(),
                    rowHeight
            );
            contentParams.leftMargin = rowHeaderWidth;
            cellContainer.setLayoutParams(contentParams);

            FrameLayout.LayoutParams rowNumberParams = new FrameLayout.LayoutParams(
                    rowHeaderWidth,
                    rowHeight
            );
            rowNumberView.setLayoutParams(rowNumberParams);
            rowNumberView.setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize());
        }

        void applyHorizontalOffset() {
            cellContainer.setTranslationX(-horizontalOffset);
        }

        private void applyCellPresentation(
                SpreadsheetCellView cell,
                SpreadsheetCellStyle style,
                String value
        ) {
            cell.setGravity(gravityFor(style, value));
            cell.setTypeface(typefaceFor(style));
            cell.setPaintFlags(style.isUnderline()
                    ? cell.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG
                    : cell.getPaintFlags() & ~Paint.UNDERLINE_TEXT_FLAG);
            cell.setTextColor(style.getFontColor() == null
                    ? defaultTextColor
                    : style.getFontColor());
            cell.setTextSize(
                    TypedValue.COMPLEX_UNIT_PX,
                    getScaledTextSize(style)
            );
            int padding = getScaledPadding();
            cell.setPadding(padding, Math.max(0, padding / 3), padding, Math.max(0, padding / 3));
            cell.setRenderStyle(style, ZoomController.isOverview(zoomMode));
            cell.setBackground(new SpreadsheetCellDrawable(
                    style,
                    defaultCellColor,
                    gridColor,
                    density
            ));
        }

        private void ensureCellCount() {
            while (cellContainer.getChildCount() < scaledColumnWidths.length) {
                cellContainer.addView(new SpreadsheetCellView(rowView.getContext()));
            }
            while (cellContainer.getChildCount() > scaledColumnWidths.length) {
                cellContainer.removeViewAt(cellContainer.getChildCount() - 1);
            }
        }
    }

    private static final class RowTextMetrics {
        private static final RowTextMetrics EMPTY =
                new RowTextMetrics(null, Collections.emptyList());

        @Nullable private final SpreadsheetCellStyle tallestSingleLineStyle;
        private final List<WrappedCell> wrappedCells;

        private RowTextMetrics(
                @Nullable SpreadsheetCellStyle tallestSingleLineStyle,
                List<WrappedCell> wrappedCells
        ) {
            this.tallestSingleLineStyle = tallestSingleLineStyle;
            this.wrappedCells = wrappedCells;
        }
    }

    private static final class WrappedCell {
        private final String value;
        private final int column;
        private final SpreadsheetCellStyle style;
        @Nullable private final SpreadsheetMergedRange mergedRange;

        private WrappedCell(
                String value,
                int column,
                SpreadsheetCellStyle style,
                @Nullable SpreadsheetMergedRange mergedRange
        ) {
            this.value = value;
            this.column = column;
            this.style = style;
            this.mergedRange = mergedRange;
        }
    }
}
