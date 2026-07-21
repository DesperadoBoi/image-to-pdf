package com.desperadoboi.imagetopdf.ui.editor;

import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.desperadoboi.imagetopdf.R;
import com.desperadoboi.imagetopdf.image.ThumbnailLoader;
import com.desperadoboi.imagetopdf.model.PageItem;

import java.util.List;

public final class PageEditStripAdapter
        extends RecyclerView.Adapter<PageEditStripAdapter.PageStripViewHolder> {
    private final List<PageItem> pages;
    private final ThumbnailLoader thumbnailLoader;
    private final OnPageSelectedListener listener;
    private long selectedPageId;

    public PageEditStripAdapter(
            List<PageItem> pages,
            ThumbnailLoader thumbnailLoader,
            long selectedPageId,
            OnPageSelectedListener listener
    ) {
        this.pages = pages;
        this.thumbnailLoader = thumbnailLoader;
        this.selectedPageId = selectedPageId;
        this.listener = listener;
        setHasStableIds(true);
    }

    @NonNull
    @Override
    public PageStripViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(
                R.layout.item_page_edit_strip,
                parent,
                false
        );
        return new PageStripViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PageStripViewHolder holder, int position) {
        PageItem page = pages.get(position);
        int pageNumber = position + 1;
        holder.itemView.setSelected(page.getId() == selectedPageId);
        holder.pageNumber.setText(
                holder.itemView.getContext().getString(R.string.page_number_label, pageNumber)
        );
        holder.itemView.setContentDescription(
                holder.itemView.getContext().getString(
                        R.string.page_edit_thumbnail_content_description,
                        pageNumber
                )
        );
        holder.itemView.setOnClickListener(view -> {
            int adapterPosition = holder.getBindingAdapterPosition();
            if (adapterPosition != RecyclerView.NO_POSITION) {
                listener.onPageSelected(pages.get(adapterPosition).getId());
            }
        });
        bindThumbnail(holder, page);
    }

    @Override
    public void onViewRecycled(@NonNull PageStripViewHolder holder) {
        holder.boundKey = null;
        holder.thumbnail.setTag(null);
        holder.thumbnail.setImageDrawable(null);
        super.onViewRecycled(holder);
    }

    @Override
    public long getItemId(int position) {
        return pages.get(position).getId();
    }

    @Override
    public int getItemCount() {
        return pages.size();
    }

    public void setSelectedPageId(long pageId) {
        if (selectedPageId == pageId) {
            return;
        }
        int oldPosition = PreviewPageNavigator.findPositionById(pages, selectedPageId);
        selectedPageId = pageId;
        int newPosition = PreviewPageNavigator.findPositionById(pages, selectedPageId);
        if (oldPosition != PreviewPageNavigator.POSITION_NOT_FOUND) {
            notifyItemChanged(oldPosition);
        }
        if (newPosition != PreviewPageNavigator.POSITION_NOT_FOUND) {
            notifyItemChanged(newPosition);
        }
    }

    public void notifyPageChanged(long pageId) {
        int position = PreviewPageNavigator.findPositionById(pages, pageId);
        if (position != PreviewPageNavigator.POSITION_NOT_FOUND) {
            notifyItemChanged(position);
        }
    }

    private void bindThumbnail(PageStripViewHolder holder, PageItem page) {
        String key = page.getThumbnailKey();
        if (key.equals(holder.boundKey)) {
            return;
        }
        holder.boundKey = key;
        holder.thumbnail.setTag(key);
        int size = holder.itemView.getResources().getDimensionPixelSize(
                R.dimen.page_edit_strip_thumbnail_size
        );
        thumbnailLoader.load(page, size, size, new ThumbnailLoader.Callback() {
            @Override
            public void onLoaded(String loadedKey, Bitmap bitmap) {
                if (!loadedKey.equals(holder.thumbnail.getTag())) {
                    recycle(bitmap);
                    return;
                }
                holder.thumbnail.setImageBitmap(bitmap);
            }

            @Override
            public void onError(String loadedKey) {
                if (loadedKey.equals(holder.thumbnail.getTag())) {
                    holder.thumbnail.setImageResource(R.drawable.ic_broken_image_24);
                }
            }
        });
    }

    private static void recycle(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }

    public interface OnPageSelectedListener {
        void onPageSelected(long pageId);
    }

    static final class PageStripViewHolder extends RecyclerView.ViewHolder {
        private final ImageView thumbnail;
        private final TextView pageNumber;
        private String boundKey;

        PageStripViewHolder(@NonNull View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.image_page_edit_strip_thumbnail);
            pageNumber = itemView.findViewById(R.id.text_page_edit_strip_number);
        }
    }
}
