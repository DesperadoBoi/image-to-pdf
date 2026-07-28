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
import com.desperadoboi.imagetopdf.model.PageDragStartGate;
import com.desperadoboi.imagetopdf.model.PageItem;

import java.util.List;

public final class EditorPageStripAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int VIEW_TYPE_PAGE = 0;
    private static final int VIEW_TYPE_ADD = 1;

    private final List<PageItem> pages;
    private final ThumbnailLoader thumbnailLoader;
    private final Listener listener;
    private final PageDragStartGate dragStartGate = new PageDragStartGate();
    private final boolean showAddItem;
    private long selectedPageId;
    private boolean actionsEnabled = true;

    public EditorPageStripAdapter(List<PageItem> pages, ThumbnailLoader thumbnailLoader,
            long selectedPageId, boolean showAddItem, Listener listener) {
        this.pages = pages;
        this.thumbnailLoader = thumbnailLoader;
        this.selectedPageId = selectedPageId;
        this.listener = listener;
        this.showAddItem = showAddItem;
        setHasStableIds(true);
    }

    @Override public int getItemViewType(int position) {
        return showAddItem && position == pages.size() ? VIEW_TYPE_ADD : VIEW_TYPE_PAGE;
    }

    @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        int layout = type == VIEW_TYPE_ADD ? R.layout.item_editor_add_page : R.layout.item_page_edit_strip;
        if (type == VIEW_TYPE_ADD) {
            return new AddHolder(LayoutInflater.from(parent.getContext()).inflate(layout, parent, false));
        }
        PageHolder holder = new PageHolder(LayoutInflater.from(parent.getContext()).inflate(layout, parent, false));
        holder.thumbnail.setOnLongClickListener(view -> startDrag(holder));
        return holder;
    }

    @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof AddHolder) {
            holder.itemView.setEnabled(actionsEnabled);
            holder.itemView.setOnClickListener(view -> { if (actionsEnabled) listener.onAddRequested(); });
            return;
        }
        PageHolder pageHolder = (PageHolder) holder;
        PageItem page = pages.get(position);
        pageHolder.itemView.setSelected(page.getId() == selectedPageId);
        pageHolder.itemView.setEnabled(actionsEnabled);
        pageHolder.number.setText(pageHolder.itemView.getContext().getString(R.string.page_number_label, position + 1));
        pageHolder.itemView.setContentDescription(pageHolder.itemView.getContext().getString(R.string.page_edit_thumbnail_content_description, position + 1));
        pageHolder.itemView.setOnClickListener(view -> { if (actionsEnabled) listener.onPageSelected(page.getId()); });
        pageHolder.thumbnail.setEnabled(actionsEnabled);
        bindThumbnail(pageHolder, page);
    }

    @Override public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        if (holder instanceof PageHolder) {
            PageHolder pageHolder = (PageHolder) holder;
            pageHolder.key = null;
            pageHolder.thumbnail.setTag(null);
            pageHolder.thumbnail.setImageDrawable(null);
        }
        super.onViewRecycled(holder);
    }

    @Override public long getItemId(int position) {
        return showAddItem && position == pages.size() ? Long.MIN_VALUE : pages.get(position).getId();
    }
    @Override public int getItemCount() { return pages.size() + (showAddItem ? 1 : 0); }

    public void setSelectedPageId(long pageId) {
        if (selectedPageId == pageId) return;
        int oldPosition = PreviewPageNavigator.findPositionById(pages, selectedPageId);
        selectedPageId = pageId;
        int newPosition = PreviewPageNavigator.findPositionById(pages, selectedPageId);
        if (oldPosition != PreviewPageNavigator.POSITION_NOT_FOUND) notifyItemChanged(oldPosition);
        if (newPosition != PreviewPageNavigator.POSITION_NOT_FOUND) notifyItemChanged(newPosition);
    }

    public void setActionsEnabled(boolean enabled) {
        if (actionsEnabled == enabled) return;
        actionsEnabled = enabled;
        notifyItemRangeChanged(0, getItemCount());
    }

    public void notifyPageChanged(long pageId) {
        int position = PreviewPageNavigator.findPositionById(pages, pageId);
        if (position != PreviewPageNavigator.POSITION_NOT_FOUND) notifyItemChanged(position);
    }

    public void onDragFinished(RecyclerView.ViewHolder viewHolder) {
        dragStartGate.finish(viewHolder.getItemId());
    }

    private boolean startDrag(PageHolder holder) {
        int position = holder.getBindingAdapterPosition();
        if (!actionsEnabled || position == RecyclerView.NO_POSITION || position >= pages.size()
                || !dragStartGate.tryStart(holder.getItemId())) {
            return false;
        }
        boolean started = listener.onPageDragStart(holder);
        if (!started) {
            dragStartGate.finish(holder.getItemId());
        }
        return started;
    }

    private void bindThumbnail(PageHolder holder, PageItem page) {
        String key = page.getThumbnailKey();
        if (key.equals(holder.key)) return;
        holder.key = key;
        holder.thumbnail.setTag(key);
        holder.thumbnail.setImageDrawable(null);
        int size = holder.itemView.getResources().getDimensionPixelSize(R.dimen.page_edit_strip_thumbnail_size);
        thumbnailLoader.load(page, size, size, new ThumbnailLoader.Callback() {
            @Override public void onLoaded(String loadedKey, Bitmap bitmap) {
                if (!loadedKey.equals(holder.thumbnail.getTag())) { recycle(bitmap); return; }
                holder.thumbnail.setImageBitmap(bitmap);
            }
            @Override public void onError(String loadedKey) {
                if (loadedKey.equals(holder.thumbnail.getTag())) holder.thumbnail.setImageResource(R.drawable.ic_broken_image_24);
            }
        });
    }

    private static void recycle(Bitmap bitmap) { if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle(); }

    public interface Listener {
        void onPageSelected(long pageId);
        void onAddRequested();
        boolean onPageDragStart(RecyclerView.ViewHolder viewHolder);
    }
    private static final class PageHolder extends RecyclerView.ViewHolder {
        final ImageView thumbnail; final TextView number; String key;
        PageHolder(View view) { super(view); thumbnail = view.findViewById(R.id.image_page_edit_strip_thumbnail); number = view.findViewById(R.id.text_page_edit_strip_number); }
    }
    private static final class AddHolder extends RecyclerView.ViewHolder { AddHolder(View view) { super(view); } }
}
