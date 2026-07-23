package com.desperadoboi.imagetopdf.ui.viewer;

import android.graphics.Bitmap;
import android.graphics.text.LineBreaker;
import android.os.Build;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.desperadoboi.imagetopdf.R;
import com.desperadoboi.imagetopdf.document.word.WordBlock;
import com.desperadoboi.imagetopdf.document.word.WordDocumentModel;
import com.desperadoboi.imagetopdf.document.word.WordImage;
import com.desperadoboi.imagetopdf.document.word.WordImageLoader;
import com.desperadoboi.imagetopdf.document.word.WordPageBreak;
import com.desperadoboi.imagetopdf.document.word.WordParagraph;
import com.desperadoboi.imagetopdf.document.word.WordParagraphStyle;
import com.desperadoboi.imagetopdf.document.word.WordTable;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

final class WordBlockAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int VIEW_PARAGRAPH = 1;
    private static final int VIEW_TABLE = 2;
    private static final int VIEW_IMAGE = 3;
    private static final int VIEW_PAGE_BREAK = 4;

    private final WordImageLoader imageLoader;
    private final WordSpannableFactory.LinkHandler linkHandler;
    private List<WordBlock> blocks = Collections.emptyList();

    WordBlockAdapter(
            File packageFile,
            Executor callbackExecutor,
            WordSpannableFactory.LinkHandler linkHandler
    ) {
        imageLoader = new WordImageLoader(packageFile, callbackExecutor);
        this.linkHandler = linkHandler;
        setHasStableIds(true);
    }

    void submit(WordDocumentModel document) {
        blocks = document == null ? Collections.emptyList() : document.getBlocks();
        notifyDataSetChanged();
    }

    void close() {
        imageLoader.close();
        blocks = Collections.emptyList();
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getItemViewType(int position) {
        WordBlock.Type type = blocks.get(position).getType();
        if (type == WordBlock.Type.TABLE) return VIEW_TABLE;
        if (type == WordBlock.Type.IMAGE) return VIEW_IMAGE;
        if (type == WordBlock.Type.PAGE_BREAK) return VIEW_PAGE_BREAK;
        return VIEW_PARAGRAPH;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TABLE) {
            return new TableHolder(inflater.inflate(
                    R.layout.item_word_table,
                    parent,
                    false
            ));
        }
        if (viewType == VIEW_IMAGE) {
            return new ImageHolder(inflater.inflate(
                    R.layout.item_word_image,
                    parent,
                    false
            ));
        }
        if (viewType == VIEW_PAGE_BREAK) {
            return new PageBreakHolder(inflater.inflate(
                    R.layout.item_word_page_break,
                    parent,
                    false
            ));
        }
        return new ParagraphHolder(inflater.inflate(
                R.layout.item_word_paragraph,
                parent,
                false
        ));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        WordBlock block = blocks.get(position);
        if (holder instanceof ParagraphHolder) {
            bindParagraph((ParagraphHolder) holder, (WordParagraph) block);
        } else if (holder instanceof TableHolder) {
            WordTable table = (WordTable) block;
            ((TableHolder) holder).table.submit(table);
            holder.itemView.setContentDescription(holder.itemView.getContext().getString(
                    R.string.viewer_word_table_content_description,
                    table.getRows().size(),
                    table.getColumnWidthsTwips().size()
            ));
        } else if (holder instanceof ImageHolder) {
            bindImage((ImageHolder) holder, (WordImage) block);
        }
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        if (holder instanceof ImageHolder) {
            ImageHolder imageHolder = (ImageHolder) holder;
            imageHolder.boundPath = null;
            imageHolder.image.setImageDrawable(null);
        } else if (holder instanceof TableHolder) {
            ((TableHolder) holder).table.clear();
        } else if (holder instanceof ParagraphHolder) {
            ((ParagraphHolder) holder).text.setText(null);
        }
        super.onViewRecycled(holder);
    }

    @Override
    public int getItemCount() {
        return blocks.size();
    }

    private void bindParagraph(ParagraphHolder holder, WordParagraph paragraph) {
        TextView text = holder.text;
        float density = text.getResources().getDisplayMetrics().density;
        text.setText(WordSpannableFactory.create(paragraph, density, linkHandler));
        text.setMovementMethod(LinkMovementMethod.getInstance());
        text.setTextIsSelectable(true);
        WordParagraphStyle style = paragraph.getStyle();
        if (style.getAlignment() == WordParagraphStyle.Alignment.CENTER) {
            text.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        } else if (style.getAlignment() == WordParagraphStyle.Alignment.RIGHT) {
            text.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
        } else {
            text.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            text.setJustificationMode(style.getAlignment()
                    == WordParagraphStyle.Alignment.JUSTIFY
                    ? LineBreaker.JUSTIFICATION_MODE_INTER_WORD
                    : LineBreaker.JUSTIFICATION_MODE_NONE);
        }
        int baseHorizontal = text.getResources().getDimensionPixelSize(
                R.dimen.viewer_word_paragraph_horizontal_padding
        );
        int left = Math.max(0, Math.round(style.getLeftIndentTwips() * density / 9f));
        int right = Math.max(0, Math.round(style.getRightIndentTwips() * density / 9f));
        text.setPadding(
                baseHorizontal + left,
                text.getPaddingTop(),
                baseHorizontal + right,
                text.getPaddingBottom()
        );
        ViewGroup.MarginLayoutParams layoutParams =
                (ViewGroup.MarginLayoutParams) text.getLayoutParams();
        layoutParams.topMargin = Math.max(
                0,
                Math.round(style.getSpaceBeforeTwips() * density / 9f)
        );
        layoutParams.bottomMargin = Math.max(
                0,
                Math.round(style.getSpaceAfterTwips() * density / 9f)
        );
        text.setLayoutParams(layoutParams);
        if (style.getLineSpacingTwips() > 0) {
            text.setLineSpacing(
                    Math.max(0f, style.getLineSpacingTwips() * density / 9f
                            - text.getTextSize()),
                    1f
            );
        } else {
            text.setLineSpacing(0f, 1f);
        }
        boolean secondary = paragraph.getRole() != WordParagraph.Role.BODY;
        text.setAlpha(secondary ? 0.82f : 1f);
    }

    private void bindImage(ImageHolder holder, WordImage image) {
        holder.boundPath = image.getPackagePath();
        holder.image.setImageDrawable(null);
        holder.progress.setVisibility(image.isVectorPlaceholder()
                ? View.GONE : View.VISIBLE);
        holder.placeholder.setVisibility(image.isVectorPlaceholder()
                ? View.VISIBLE : View.GONE);
        holder.placeholder.setText(image.isVectorPlaceholder()
                ? R.string.viewer_word_vector_placeholder
                : R.string.viewer_word_image_failed);
        CharSequence description = image.getAltText().isEmpty()
                ? holder.itemView.getContext().getText(
                        R.string.viewer_word_image_content_description
                )
                : image.getAltText();
        holder.image.setContentDescription(description);
        holder.placeholder.setContentDescription(description);
        holder.image.post(() -> {
            if (!image.getPackagePath().equals(holder.boundPath)
                    || image.isVectorPlaceholder()) {
                return;
            }
            int width = Math.max(1, holder.container.getWidth());
            int height = desiredImageHeight(image, width, holder);
            ViewGroup.LayoutParams imageLayout = holder.image.getLayoutParams();
            imageLayout.height = height;
            holder.image.setLayoutParams(imageLayout);
            imageLoader.load(
                    image.getPackagePath(),
                    width * 2,
                    height * 2,
                    new WordImageLoader.Callback() {
                        @Override
                        public void onLoaded(String packagePath, Bitmap bitmap) {
                            if (!packagePath.equals(holder.boundPath)) return;
                            holder.progress.setVisibility(View.GONE);
                            holder.placeholder.setVisibility(View.GONE);
                            holder.image.setImageBitmap(bitmap);
                        }

                        @Override
                        public void onError(String packagePath, Exception exception) {
                            if (!packagePath.equals(holder.boundPath)) return;
                            holder.progress.setVisibility(View.GONE);
                            holder.placeholder.setVisibility(View.VISIBLE);
                        }
                    }
            );
        });
    }

    private int desiredImageHeight(WordImage image, int width, ImageHolder holder) {
        int maximum = holder.itemView.getResources().getDimensionPixelSize(
                R.dimen.viewer_word_image_max_height
        );
        int minimum = holder.itemView.getResources().getDimensionPixelSize(
                R.dimen.viewer_word_image_min_height
        );
        return WordImageSizeCalculator.calculateHeight(
                image.getWidthEmu(),
                image.getHeightEmu(),
                width,
                minimum,
                maximum
        );
    }

    private static final class ParagraphHolder extends RecyclerView.ViewHolder {
        private final TextView text;

        private ParagraphHolder(View itemView) {
            super(itemView);
            text = (TextView) itemView;
        }
    }

    private static final class TableHolder extends RecyclerView.ViewHolder {
        private final WordTableView table;

        private TableHolder(View itemView) {
            super(itemView);
            table = itemView.findViewById(R.id.word_table_view);
        }
    }

    private static final class ImageHolder extends RecyclerView.ViewHolder {
        private final FrameLayout container;
        private final ImageView image;
        private final TextView placeholder;
        private final CircularProgressIndicator progress;
        private String boundPath;

        private ImageHolder(View itemView) {
            super(itemView);
            container = (FrameLayout) itemView;
            image = itemView.findViewById(R.id.image_word_block);
            placeholder = itemView.findViewById(R.id.text_word_image_placeholder);
            progress = itemView.findViewById(R.id.progress_word_image);
        }
    }

    private static final class PageBreakHolder extends RecyclerView.ViewHolder {
        private PageBreakHolder(View itemView) {
            super(itemView);
        }
    }
}
