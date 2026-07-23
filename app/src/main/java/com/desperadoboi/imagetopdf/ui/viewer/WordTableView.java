package com.desperadoboi.imagetopdf.ui.viewer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.OverScroller;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

import com.desperadoboi.imagetopdf.R;
import com.desperadoboi.imagetopdf.document.word.WordBorder;
import com.desperadoboi.imagetopdf.document.word.WordTable;
import com.desperadoboi.imagetopdf.document.word.WordTableCell;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class WordTableView extends View {
    private static final int MAX_LAYOUT_CACHE = 64;

    private final float density;
    private final int maximumHeight;
    private final Paint fillPaint = new Paint();
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint textPaint =
            new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final RectF reusableBounds = new RectF();
    private final GestureDetector gestureDetector;
    private final OverScroller scroller;
    private final Map<Integer, StaticLayout> layoutCache =
            new LinkedHashMap<Integer, StaticLayout>(MAX_LAYOUT_CACHE, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Integer, StaticLayout> eldest) {
                    return size() > MAX_LAYOUT_CACHE;
                }
            };

    @Nullable private WordTable table;
    @Nullable private WordTableGeometry geometry;
    private float offsetX;
    private float offsetY;
    private int lastFlingX;
    private int lastFlingY;
    private boolean touchMoved;

    public WordTableView(Context context) {
        this(context, null);
    }

    public WordTableView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WordTableView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        density = getResources().getDisplayMetrics().density;
        maximumHeight = getResources().getDimensionPixelSize(
                R.dimen.viewer_word_table_max_height
        );
        fillPaint.setColor(Color.WHITE);
        borderPaint.setStyle(Paint.Style.STROKE);
        textPaint.setColor(ContextCompat.getColor(context, R.color.viewer_document_text));
        textPaint.setTextSize(getResources().getDimension(
                R.dimen.viewer_word_table_text_size
        ));
        scroller = new OverScroller(context);
        gestureDetector = new GestureDetector(context, new GestureListener());
        setClickable(true);
        setFocusable(true);
    }

    void submit(WordTable table) {
        this.table = table;
        geometry = WordTableGeometry.create(table, density);
        offsetX = 0f;
        offsetY = 0f;
        layoutCache.clear();
        requestLayout();
        invalidate();
    }

    void clear() {
        table = null;
        geometry = null;
        layoutCache.clear();
        if (!scroller.isFinished()) scroller.abortAnimation();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        WordTableGeometry current = geometry;
        int desiredHeight = current == null
                ? getSuggestedMinimumHeight()
                : Math.max(
                        getResources().getDimensionPixelSize(
                                R.dimen.viewer_word_table_min_height
                        ),
                        Math.min(maximumHeight, Math.round(current.getHeight()))
                );
        setMeasuredDimension(
                resolveSize(width, widthMeasureSpec),
                resolveSize(desiredHeight, heightMeasureSpec)
        );
        clampOffsets();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        WordTable currentTable = table;
        WordTableGeometry currentGeometry = geometry;
        if (currentTable == null || currentGeometry == null
                || currentGeometry.getRowCount() == 0) {
            return;
        }
        canvas.drawColor(Color.WHITE);
        int firstRow = currentGeometry.firstVisibleRow(offsetY);
        int lastRow = currentGeometry.lastVisibleRow(offsetY + getHeight());
        Set<Integer> drawnPlacements = new HashSet<>();
        for (int row = firstRow; row <= lastRow; row++) {
            for (WordTableGeometry.CellPlacement placement
                    : currentGeometry.getPlacements(row)) {
                if (!drawnPlacements.add(placement.getId())) continue;
                float left = alignedX(currentTable, currentGeometry)
                        + currentGeometry.getColumnLeft(placement.getFirstColumn())
                        - offsetX;
                float right = alignedX(currentTable, currentGeometry)
                        + currentGeometry.getColumnRight(placement.getLastColumn())
                        - offsetX;
                float top = currentGeometry.getRowTop(placement.getFirstRow()) - offsetY;
                float bottom = currentGeometry.getRowBottom(placement.getLastRow()) - offsetY;
                if (right <= 0f || left >= getWidth() || bottom <= 0f || top >= getHeight()) {
                    continue;
                }
                reusableBounds.set(left, top, right, bottom);
                drawCell(canvas, currentTable, placement, reusableBounds);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (geometry == null) return super.onTouchEvent(event);
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            touchMoved = false;
            if (!scroller.isFinished()) {
                scroller.abortAnimation();
            }
        }
        boolean handled = gestureDetector.onTouchEvent(event)
                || super.onTouchEvent(event);
        if (event.getActionMasked() == MotionEvent.ACTION_UP && !touchMoved) {
            performClick();
        }
        if ((event.getActionMasked() == MotionEvent.ACTION_UP
                || event.getActionMasked() == MotionEvent.ACTION_CANCEL)
                && getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(false);
        }
        return handled;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    public void computeScroll() {
        if (!scroller.computeScrollOffset()) return;
        int x = scroller.getCurrX();
        int y = scroller.getCurrY();
        offsetX += x - lastFlingX;
        offsetY += y - lastFlingY;
        lastFlingX = x;
        lastFlingY = y;
        clampOffsets();
        ViewCompat.postInvalidateOnAnimation(this);
    }

    private void drawCell(
            Canvas canvas,
            WordTable currentTable,
            WordTableGeometry.CellPlacement placement,
            RectF bounds
    ) {
        WordTableCell cell = placement.getCell();
        fillPaint.setColor(cell.getShadingColor() == null
                ? Color.WHITE
                : cell.getShadingColor());
        canvas.drawRect(bounds, fillPaint);
        drawBorder(canvas, bounds.left, bounds.top, bounds.right, bounds.top,
                choose(cell.getTopBorder(), placement.getFirstRow() == 0
                        ? currentTable.getTopBorder()
                        : currentTable.getInsideHorizontalBorder()));
        drawBorder(canvas, bounds.left, bounds.bottom, bounds.right, bounds.bottom,
                choose(cell.getBottomBorder(), placement.getLastRow()
                        == geometry.getRowCount() - 1
                        ? currentTable.getBottomBorder()
                        : currentTable.getInsideHorizontalBorder()));
        drawBorder(canvas, bounds.left, bounds.top, bounds.left, bounds.bottom,
                choose(cell.getLeftBorder(), placement.getFirstColumn() == 0
                        ? currentTable.getLeftBorder()
                        : currentTable.getInsideVerticalBorder()));
        drawBorder(canvas, bounds.right, bounds.top, bounds.right, bounds.bottom,
                choose(cell.getRightBorder(), placement.getLastColumn()
                        == geometry.getColumnCount() - 1
                        ? currentTable.getRightBorder()
                        : currentTable.getInsideVerticalBorder()));

        String value = cell.getPlainText();
        if (value.isEmpty()) return;
        int padding = Math.max(
                getResources().getDimensionPixelSize(R.dimen.viewer_word_table_cell_padding),
                Math.round(currentTable.getCellMarginTwips() * density / 9f)
        );
        int availableWidth = Math.max(1, Math.round(bounds.width()) - padding * 2);
        StaticLayout layout = layoutCache.get(placement.getId());
        if (layout == null || layout.getWidth() != availableWidth) {
            layout = StaticLayout.Builder.obtain(
                            value,
                            0,
                            value.length(),
                            textPaint,
                            availableWidth
                    )
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setIncludePad(false)
                    .setMaxLines(10)
                    .build();
            layoutCache.put(placement.getId(), layout);
        }
        float contentHeight = layout.getHeight();
        float textTop = bounds.top + padding;
        if (cell.getVerticalAlignment() == WordTableCell.VerticalAlignment.CENTER) {
            textTop = bounds.top + Math.max(
                    padding,
                    (bounds.height() - contentHeight) / 2f
            );
        } else if (cell.getVerticalAlignment()
                == WordTableCell.VerticalAlignment.BOTTOM) {
            textTop = Math.max(
                    bounds.top + padding,
                    bounds.bottom - padding - contentHeight
            );
        }
        int save = canvas.save();
        canvas.clipRect(
                bounds.left + padding,
                bounds.top + padding,
                bounds.right - padding,
                bounds.bottom - padding
        );
        canvas.translate(bounds.left + padding, textTop);
        layout.draw(canvas);
        canvas.restoreToCount(save);
    }

    private WordBorder choose(WordBorder direct, WordBorder fallback) {
        return direct != null && direct.getStyle() != WordBorder.Style.NONE
                ? direct
                : fallback;
    }

    private void drawBorder(
            Canvas canvas,
            float startX,
            float startY,
            float endX,
            float endY,
            WordBorder border
    ) {
        WordBorder safe = border == null ? WordBorder.NONE : border;
        borderPaint.setColor(safe.getStyle() == WordBorder.Style.NONE
                ? ContextCompat.getColor(getContext(), R.color.viewer_table_grid)
                : safe.getColor());
        float width = Math.max(1f, safe.getSizeEighthPoints() * density / 8f);
        if (safe.getStyle() == WordBorder.Style.THICK
                || safe.getStyle() == WordBorder.Style.DOUBLE) {
            width = Math.max(width, 2f * density);
        }
        borderPaint.setStrokeWidth(width);
        canvas.drawLine(startX, startY, endX, endY, borderPaint);
    }

    private float alignedX(WordTable table, WordTableGeometry current) {
        if (current.getWidth() >= getWidth()) return 0f;
        if (table.getAlignment() == WordTable.Alignment.CENTER) {
            return (getWidth() - current.getWidth()) / 2f;
        }
        if (table.getAlignment() == WordTable.Alignment.RIGHT) {
            return getWidth() - current.getWidth();
        }
        return 0f;
    }

    private void clampOffsets() {
        WordTableGeometry current = geometry;
        if (current == null) {
            offsetX = 0f;
            offsetY = 0f;
            return;
        }
        offsetX = Math.max(0f, Math.min(
                offsetX,
                Math.max(0f, current.getWidth() - getWidth())
        ));
        offsetY = Math.max(0f, Math.min(
                offsetY,
                Math.max(0f, current.getHeight() - getHeight())
        ));
    }

    private final class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDown(MotionEvent event) {
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
            return true;
        }

        @Override
        public boolean onScroll(
                MotionEvent first,
                MotionEvent current,
                float distanceX,
                float distanceY
        ) {
            touchMoved = true;
            offsetX += distanceX;
            offsetY += distanceY;
            clampOffsets();
            invalidate();
            return true;
        }

        @Override
        public boolean onFling(
                MotionEvent first,
                MotionEvent second,
                float velocityX,
                float velocityY
        ) {
            WordTableGeometry current = geometry;
            if (current == null) return false;
            lastFlingX = 0;
            lastFlingY = 0;
            scroller.fling(
                    0,
                    0,
                    Math.round(-velocityX),
                    Math.round(-velocityY),
                    -Math.round(offsetX),
                    Math.round(Math.max(0f, current.getWidth() - getWidth()) - offsetX),
                    -Math.round(offsetY),
                    Math.round(Math.max(0f, current.getHeight() - getHeight()) - offsetY)
            );
            ViewCompat.postInvalidateOnAnimation(WordTableView.this);
            return true;
        }
    }
}
