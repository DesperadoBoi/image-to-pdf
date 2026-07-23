package com.desperadoboi.imagetopdf.ui.viewer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.style.LineHeightSpan;
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
import com.desperadoboi.imagetopdf.document.word.WordBlock;
import com.desperadoboi.imagetopdf.document.word.WordMeasurementConverter;
import com.desperadoboi.imagetopdf.document.word.WordParagraph;
import com.desperadoboi.imagetopdf.document.word.WordParagraphStyle;
import com.desperadoboi.imagetopdf.document.word.WordTable;
import com.desperadoboi.imagetopdf.document.word.WordTableCell;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class WordTableView extends View {
    private static final int MAX_LAYOUT_CACHE = 64;

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
    private WordMeasurementConverter measurementConverter;
    private int geometryAvailableWidth = -1;
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
        measurementConverter = new WordMeasurementConverter(
                getResources().getDisplayMetrics().xdpi,
                getResources().getDisplayMetrics().density,
                getResources().getConfiguration().fontScale
        );
        maximumHeight = getResources().getDimensionPixelSize(
                R.dimen.viewer_word_table_max_height
        );
        fillPaint.setColor(Color.WHITE);
        borderPaint.setStyle(Paint.Style.STROKE);
        textPaint.setColor(ContextCompat.getColor(context, R.color.viewer_document_text));
        textPaint.setTextSize(measurementConverter.fontPointsToPixels(11f));
        scroller = new OverScroller(context);
        gestureDetector = new GestureDetector(context, new GestureListener());
        setClickable(true);
        setFocusable(true);
    }

    void submit(
            WordTable table,
            WordMeasurementConverter measurementConverter
    ) {
        this.table = table;
        this.measurementConverter = measurementConverter;
        textPaint.setTextSize(measurementConverter.fontPointsToPixels(11f));
        geometryAvailableWidth = -1;
        geometry = null;
        offsetX = 0f;
        offsetY = 0f;
        layoutCache.clear();
        requestLayout();
        invalidate();
    }

    void clear() {
        table = null;
        geometry = null;
        geometryAvailableWidth = -1;
        layoutCache.clear();
        if (!scroller.isFinished()) scroller.abortAnimation();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        ensureGeometry(width);
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

    private void ensureGeometry(int availableWidth) {
        WordTable currentTable = table;
        int safeWidth = Math.max(1, availableWidth);
        if (currentTable == null || (geometry != null
                && geometryAvailableWidth == safeWidth)) {
            return;
        }
        geometry = WordTableGeometry.create(
                currentTable,
                measurementConverter,
                safeWidth
        );
        geometryAvailableWidth = safeWidth;
        layoutCache.clear();
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

        int paddingStart = Math.max(0, Math.round(
                measurementConverter.twipsToPixels(
                        currentTable.getCellMarginStartTwips()
                )
        ));
        int paddingEnd = Math.max(0, Math.round(
                measurementConverter.twipsToPixels(
                        currentTable.getCellMarginEndTwips()
                )
        ));
        int paddingTop = Math.max(
                0,
                Math.round(measurementConverter.twipsToPixels(
                        currentTable.getCellMarginTopTwips()
                ))
        );
        int paddingBottom = Math.max(
                0,
                Math.round(measurementConverter.twipsToPixels(
                        currentTable.getCellMarginBottomTwips()
                ))
        );
        int availableWidth = Math.max(
                1,
                Math.round(bounds.width()) - paddingStart - paddingEnd
                        - maximumEndIndent(cell)
        );
        StaticLayout layout = layoutCache.get(placement.getId());
        if (layout == null || layout.getWidth() != availableWidth) {
            SpannableStringBuilder value = cellText(cell);
            if (value.length() == 0) return;
            layout = StaticLayout.Builder.obtain(
                            value,
                            0,
                            value.length(),
                            textPaint,
                            availableWidth
                    )
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setIncludePad(false)
                    .setLineSpacing(0f, 1f)
                    .build();
            layoutCache.put(placement.getId(), layout);
        }
        float contentHeight = layout.getHeight();
        float textTop = bounds.top + paddingTop;
        if (cell.getVerticalAlignment() == WordTableCell.VerticalAlignment.CENTER) {
            textTop = bounds.top + Math.max(
                    paddingTop,
                    (bounds.height() - contentHeight) / 2f
            );
        } else if (cell.getVerticalAlignment()
                == WordTableCell.VerticalAlignment.BOTTOM) {
            textTop = Math.max(
                    bounds.top + paddingTop,
                    bounds.bottom - paddingBottom - contentHeight
            );
        }
        int save = canvas.save();
        canvas.clipRect(
                bounds.left + paddingStart,
                bounds.top + paddingTop,
                bounds.right - paddingEnd,
                bounds.bottom - paddingBottom
        );
        canvas.translate(bounds.left + paddingStart, textTop);
        layout.draw(canvas);
        canvas.restoreToCount(save);
    }

    private SpannableStringBuilder cellText(WordTableCell cell) {
        SpannableStringBuilder result = new SpannableStringBuilder();
        WordParagraph previous = null;
        for (int blockIndex = 0; blockIndex < cell.getBlocks().size(); blockIndex++) {
            WordBlock block = cell.getBlocks().get(blockIndex);
            if (block instanceof WordParagraph) {
                WordParagraph paragraph = (WordParagraph) block;
                WordParagraph next = null;
                if (blockIndex + 1 < cell.getBlocks().size()
                        && cell.getBlocks().get(blockIndex + 1)
                        instanceof WordParagraph) {
                    next = (WordParagraph) cell.getBlocks().get(blockIndex + 1);
                }
                ParagraphLayoutMetrics metrics = ParagraphLayoutMetrics.resolve(
                        paragraph,
                        previous,
                        next,
                        measurementConverter
                );
                if (result.length() > 0) {
                    int separatorStart = result.length();
                    result.append('\n');
                    result.setSpan(
                            new TableParagraphGapSpan(
                                    metrics.getMarginTopPixels()
                            ),
                            separatorStart,
                            result.length(),
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    );
                }
                int paragraphStart = result.length();
                result.append(WordSpannableFactory.createForTable(
                        paragraph,
                        measurementConverter
                ));
                if (result.length() > paragraphStart) {
                    result.setSpan(
                            new TableLineHeightSpan(
                                    metrics.getLineSpacingExtraPixels(),
                                    metrics.getLineSpacingMultiplier()
                            ),
                            paragraphStart,
                            result.length(),
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    );
                }
                previous = paragraph;
            } else if (block instanceof WordTable) {
                if (result.length() > 0) result.append('\n');
                result.append('\u25A6');
                previous = null;
            }
        }
        return result;
    }

    private int maximumEndIndent(WordTableCell cell) {
        int maximum = 0;
        for (WordBlock block : cell.getBlocks()) {
            if (!(block instanceof WordParagraph)) continue;
            WordParagraph paragraph = (WordParagraph) block;
            WordParagraphStyle style = paragraph.getStyle();
            float fontPixels = measurementConverter.fontPointsToPixels(
                    paragraph.getDefaultRunStyle().getFontSizePoints()
            );
            int pixels = Math.round(style.hasRightIndent()
                    ? measurementConverter.twipsToPixels(
                            style.getRightIndentTwips()
                    )
                    : measurementConverter.characterUnitsToPixels(
                            style.getEndIndentCharacters(),
                            fontPixels
                    ));
            maximum = Math.max(maximum, pixels);
        }
        return maximum;
    }

    private static final class TableLineHeightSpan implements LineHeightSpan {
        private final float extraPixels;
        private final float multiplier;

        private TableLineHeightSpan(float extraPixels, float multiplier) {
            this.extraPixels = extraPixels;
            this.multiplier = multiplier;
        }

        @Override
        public void chooseHeight(
                CharSequence text,
                int start,
                int end,
                int spanStartVertical,
                int lineHeight,
                Paint.FontMetricsInt fontMetrics
        ) {
            int natural = fontMetrics.descent - fontMetrics.ascent;
            int additional = Math.max(
                    0,
                    Math.round(natural * (multiplier - 1f) + extraPixels)
            );
            fontMetrics.descent += additional;
            fontMetrics.bottom += additional;
        }
    }

    private static final class TableParagraphGapSpan implements LineHeightSpan {
        private final int gapPixels;

        private TableParagraphGapSpan(int gapPixels) {
            this.gapPixels = Math.max(0, gapPixels);
        }

        @Override
        public void chooseHeight(
                CharSequence text,
                int start,
                int end,
                int spanStartVertical,
                int lineHeight,
                Paint.FontMetricsInt fontMetrics
        ) {
            fontMetrics.descent += gapPixels;
            fontMetrics.bottom += gapPixels;
        }
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
        float width = Math.max(
                1f,
                measurementConverter.eighthPointsToPixels(
                        safe.getSizeEighthPoints()
                )
        );
        if (safe.getStyle() == WordBorder.Style.THICK
                || safe.getStyle() == WordBorder.Style.DOUBLE) {
            width = Math.max(
                    width,
                    measurementConverter.pointsToPhysicalPixels(1.5f)
            );
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
