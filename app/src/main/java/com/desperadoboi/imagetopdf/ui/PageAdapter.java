package com.desperadoboi.imagetopdf.ui;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.desperadoboi.imagetopdf.R;
import com.desperadoboi.imagetopdf.image.ThumbnailLoader;
import com.desperadoboi.imagetopdf.model.PageItem;

import java.util.ArrayList;
import java.util.List;

public final class PageAdapter extends RecyclerView.Adapter<PageAdapter.PageViewHolder> {
    private final ThumbnailLoader thumbnailLoader;
    private final PageActionCallback callback;
    private final List<PageItem> pageItems = new ArrayList<>();
    private boolean actionsEnabled = true;

    public PageAdapter(ThumbnailLoader thumbnailLoader, PageActionCallback callback) {
        this.thumbnailLoader = thumbnailLoader;
        this.callback = callback;
    }

    public void submitPages(List<PageItem> newPageItems) {
        pageItems.clear();
        pageItems.addAll(newPageItems);
        notifyDataSetChanged();
    }

    public void submitMovedPages(List<PageItem> newPageItems, int fromPosition, int toPosition) {
        pageItems.clear();
        pageItems.addAll(newPageItems);
        notifyItemMoved(fromPosition, toPosition);
        int firstChangedPosition = Math.min(fromPosition, toPosition);
        int changedItemCount = Math.abs(fromPosition - toPosition) + 1;
        notifyItemRangeChanged(firstChangedPosition, changedItemCount);
    }

    public void setActionsEnabled(boolean actionsEnabled) {
        if (this.actionsEnabled == actionsEnabled) {
            return;
        }
        this.actionsEnabled = actionsEnabled;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_page, parent, false);
        return new PageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PageViewHolder holder, int position) {
        PageItem pageItem = pageItems.get(position);
        int pageNumber = position + 1;
        String thumbnailKey = pageItem.getThumbnailKey();

        holder.pageNumberTextView.setText(
                holder.itemView.getContext().getString(R.string.page_number_label, pageNumber)
        );
        holder.thumbnailImageView.setContentDescription(
                holder.itemView.getContext().getString(R.string.page_thumbnail_content_description, pageNumber)
        );
        holder.rotateButton.setContentDescription(
                holder.itemView.getContext().getString(
                        R.string.action_rotate_page_content_description,
                        pageNumber
                )
        );
        holder.deleteButton.setContentDescription(
                holder.itemView.getContext().getString(
                        R.string.action_delete_page_content_description,
                        pageNumber
                )
        );
        holder.dragHandleButton.setContentDescription(
                holder.itemView.getContext().getString(
                        R.string.action_reorder_page_content_description,
                        pageNumber
                )
        );
        holder.dragHandleButton.setEnabled(actionsEnabled);
        holder.rotateButton.setEnabled(actionsEnabled);
        holder.deleteButton.setEnabled(actionsEnabled);

        Object previousThumbnailKey = holder.thumbnailImageView.getTag();
        if (!thumbnailKey.equals(previousThumbnailKey)) {
            holder.thumbnailImageView.setTag(thumbnailKey);
            holder.thumbnailImageView.setImageDrawable(null);

            int thumbnailSize = holder.itemView.getResources().getDimensionPixelSize(R.dimen.page_thumbnail_size);
            thumbnailLoader.load(pageItem, thumbnailSize, thumbnailSize, new ThumbnailLoader.Callback() {
                @Override
                public void onLoaded(String key, Bitmap bitmap) {
                    Object currentTag = holder.thumbnailImageView.getTag();
                    if (!key.equals(currentTag)) {
                        if (!bitmap.isRecycled()) {
                            bitmap.recycle();
                        }
                        return;
                    }
                    holder.thumbnailImageView.setImageBitmap(bitmap);
                }

                @Override
                public void onError(String key) {
                    Object currentTag = holder.thumbnailImageView.getTag();
                    if (key.equals(currentTag)) {
                        holder.thumbnailImageView.setImageResource(R.drawable.ic_broken_image_24);
                    }
                }
            });
        }

        holder.rotateButton.setOnClickListener(view -> {
            int adapterPosition = holder.getBindingAdapterPosition();
            if (actionsEnabled && adapterPosition != RecyclerView.NO_POSITION) {
                callback.onRotate(adapterPosition);
            }
        });
        holder.deleteButton.setOnClickListener(view -> {
            int adapterPosition = holder.getBindingAdapterPosition();
            if (actionsEnabled && adapterPosition != RecyclerView.NO_POSITION) {
                callback.onDelete(adapterPosition);
            }
        });
        holder.dragHandleButton.setOnLongClickListener(view -> {
            int adapterPosition = holder.getBindingAdapterPosition();
            if (actionsEnabled && adapterPosition != RecyclerView.NO_POSITION) {
                callback.onDragStart(holder);
                return true;
            }
            return false;
        });
        configureMoveAccessibilityActions(holder);
    }

    @Override
    public void onViewRecycled(@NonNull PageViewHolder holder) {
        holder.thumbnailImageView.setTag(null);
        holder.thumbnailImageView.setImageDrawable(null);
        super.onViewRecycled(holder);
    }

    @Override
    public int getItemCount() {
        return pageItems.size();
    }

    public interface PageActionCallback {
        void onRotate(int position);

        void onDelete(int position);

        void onDragStart(RecyclerView.ViewHolder viewHolder);

        boolean onMoveUp(int position);

        boolean onMoveDown(int position);
    }

    private void configureMoveAccessibilityActions(PageViewHolder holder) {
        ViewCompat.setAccessibilityDelegate(holder.dragHandleButton, new AccessibilityDelegateCompat() {
            @Override
            public void onInitializeAccessibilityNodeInfo(
                    @NonNull View host,
                    @NonNull AccessibilityNodeInfoCompat info
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                int adapterPosition = holder.getBindingAdapterPosition();
                if (!actionsEnabled || adapterPosition == RecyclerView.NO_POSITION) {
                    return;
                }
                if (adapterPosition > 0) {
                    info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                            R.id.accessibility_action_move_page_up,
                            host.getContext().getString(R.string.action_move_page_up)
                    ));
                }
                if (adapterPosition < pageItems.size() - 1) {
                    info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                            R.id.accessibility_action_move_page_down,
                            host.getContext().getString(R.string.action_move_page_down)
                    ));
                }
            }

            @Override
            public boolean performAccessibilityAction(@NonNull View host, int action, Bundle args) {
                int adapterPosition = holder.getBindingAdapterPosition();
                if (!actionsEnabled || adapterPosition == RecyclerView.NO_POSITION) {
                    return super.performAccessibilityAction(host, action, args);
                }
                if (action == R.id.accessibility_action_move_page_up) {
                    return callback.onMoveUp(adapterPosition);
                }
                if (action == R.id.accessibility_action_move_page_down) {
                    return callback.onMoveDown(adapterPosition);
                }
                return super.performAccessibilityAction(host, action, args);
            }
        });
    }

    static final class PageViewHolder extends RecyclerView.ViewHolder {
        private final TextView pageNumberTextView;
        private final ImageView thumbnailImageView;
        private final ImageButton dragHandleButton;
        private final ImageButton rotateButton;
        private final ImageButton deleteButton;

        PageViewHolder(@NonNull View itemView) {
            super(itemView);
            pageNumberTextView = itemView.findViewById(R.id.text_page_number);
            thumbnailImageView = itemView.findViewById(R.id.image_page_thumbnail);
            dragHandleButton = itemView.findViewById(R.id.button_drag_page);
            rotateButton = itemView.findViewById(R.id.button_rotate_page);
            deleteButton = itemView.findViewById(R.id.button_delete_page);
        }
    }
}
