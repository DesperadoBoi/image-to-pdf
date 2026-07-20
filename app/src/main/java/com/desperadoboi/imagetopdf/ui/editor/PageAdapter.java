package com.desperadoboi.imagetopdf.ui.editor;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
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

import java.util.List;

public final class PageAdapter extends RecyclerView.Adapter<PageAdapter.PageViewHolder> {
    public static final String PAYLOAD_ROTATION = "payload_rotation";
    public static final String PAYLOAD_PAGE_NUMBER = "payload_page_number";
    public static final String PAYLOAD_ENABLED_STATE = "payload_enabled_state";

    private final List<PageItem> pageItems;
    private final ThumbnailLoader thumbnailLoader;
    private final PageActionCallback callback;
    private boolean actionsEnabled = true;

    public PageAdapter(
            List<PageItem> pageItems,
            ThumbnailLoader thumbnailLoader,
            PageActionCallback callback
    ) {
        this.pageItems = pageItems;
        this.thumbnailLoader = thumbnailLoader;
        this.callback = callback;
        setHasStableIds(true);
    }

    public void setActionsEnabled(boolean actionsEnabled) {
        if (this.actionsEnabled == actionsEnabled) {
            return;
        }
        this.actionsEnabled = actionsEnabled;
        if (!pageItems.isEmpty()) {
            notifyItemRangeChanged(0, pageItems.size(), PAYLOAD_ENABLED_STATE);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @NonNull
    @Override
    public PageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_page, parent, false);
        PageViewHolder holder = new PageViewHolder(view);
        holder.dragHandleButton.setOnTouchListener((touchedView, event) ->
                handleDragTouch(holder, event)
        );
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull PageViewHolder holder, int position) {
        bindPage(holder, position);
    }

    @Override
    public void onBindViewHolder(
            @NonNull PageViewHolder holder,
            int position,
            @NonNull List<Object> payloads
    ) {
        if (payloads.isEmpty()) {
            bindPage(holder, position);
            return;
        }

        PageItem pageItem = pageItems.get(position);
        if (payloads.contains(PAYLOAD_PAGE_NUMBER)) {
            bindPageNumberAndDescriptions(holder, position);
        }
        if (payloads.contains(PAYLOAD_ROTATION)) {
            bindThumbnail(holder, pageItem);
        }
        if (payloads.contains(PAYLOAD_ENABLED_STATE)) {
            bindEnabledState(holder);
        }
    }

    @Override
    public void onViewRecycled(@NonNull PageViewHolder holder) {
        holder.boundThumbnailKey = null;
        holder.thumbnailImageView.setTag(null);
        holder.thumbnailImageView.setImageDrawable(null);
        super.onViewRecycled(holder);
    }

    @Override
    public int getItemCount() {
        return pageItems.size();
    }

    @Override
    public long getItemId(int position) {
        return pageItems.get(position).getId();
    }

    private void bindPage(PageViewHolder holder, int position) {
        PageItem pageItem = pageItems.get(position);
        bindPageNumberAndDescriptions(holder, position);
        bindEnabledState(holder);
        bindThumbnail(holder, pageItem);
        bindClickListeners(holder);
        configureMoveAccessibilityActions(holder);
    }

    private void bindPageNumberAndDescriptions(PageViewHolder holder, int position) {
        int pageNumber = position + 1;
        holder.pageNumberTextView.setText(
                holder.itemView.getContext().getString(R.string.page_number_label, pageNumber)
        );
        holder.thumbnailImageView.setContentDescription(
                holder.itemView.getContext().getString(
                        R.string.page_thumbnail_content_description,
                        pageNumber
                )
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
    }

    private void bindEnabledState(PageViewHolder holder) {
        holder.itemView.setEnabled(actionsEnabled);
        holder.thumbnailImageView.setEnabled(actionsEnabled);
        holder.dragHandleButton.setEnabled(actionsEnabled);
        holder.rotateButton.setEnabled(actionsEnabled);
        holder.deleteButton.setEnabled(actionsEnabled);
    }

    private void bindThumbnail(PageViewHolder holder, PageItem pageItem) {
        String thumbnailKey = pageItem.getThumbnailKey();
        if (thumbnailKey.equals(holder.boundThumbnailKey)) {
            return;
        }

        String previousThumbnailKey = holder.boundThumbnailKey;
        holder.boundThumbnailKey = thumbnailKey;
        holder.thumbnailImageView.setTag(thumbnailKey);
        if (!hasSamePageId(previousThumbnailKey, thumbnailKey)) {
            holder.thumbnailImageView.setImageDrawable(null);
        }

        int thumbnailSize = holder.itemView.getResources()
                .getDimensionPixelSize(R.dimen.page_thumbnail_size);
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

    private boolean hasSamePageId(String previousThumbnailKey, String thumbnailKey) {
        if (previousThumbnailKey == null) {
            return false;
        }
        int previousSeparator = previousThumbnailKey.indexOf('#');
        int nextSeparator = thumbnailKey.indexOf('#');
        if (previousSeparator < 0 || nextSeparator < 0) {
            return false;
        }
        return previousThumbnailKey.substring(0, previousSeparator)
                .equals(thumbnailKey.substring(0, nextSeparator));
    }

    private void bindClickListeners(PageViewHolder holder) {
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
    }

    private boolean handleDragTouch(PageViewHolder holder, MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            startDragFromHandle(holder);
        }
        return false;
    }

    private void startDragFromHandle(PageViewHolder holder) {
        int adapterPosition = holder.getBindingAdapterPosition();
        if (!actionsEnabled || adapterPosition == RecyclerView.NO_POSITION) {
            return;
        }
        holder.dragHandleButton.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        callback.onDragStart(holder);
    }

    private void configureMoveAccessibilityActions(PageViewHolder holder) {
        ViewCompat.setAccessibilityDelegate(holder.itemView, new AccessibilityDelegateCompat() {
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

    public interface PageActionCallback {
        void onRotate(int position);

        void onDelete(int position);

        void onDragStart(RecyclerView.ViewHolder viewHolder);

        boolean onMoveUp(int position);

        boolean onMoveDown(int position);
    }

    static final class PageViewHolder extends RecyclerView.ViewHolder {
        private final TextView pageNumberTextView;
        private final ImageView thumbnailImageView;
        private final ImageButton dragHandleButton;
        private final ImageButton rotateButton;
        private final ImageButton deleteButton;
        private String boundThumbnailKey;

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
