package com.desperadoboi.imagetopdf.ui.editor;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
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
    private final boolean vertical;
    private long selectedPageId;
    private boolean actionsEnabled = true;

    public EditorPageStripAdapter(List<PageItem> pages, ThumbnailLoader thumbnailLoader,
            long selectedPageId, boolean showAddItem, boolean vertical, Listener listener) {
        this.pages = pages;
        this.thumbnailLoader = thumbnailLoader;
        this.selectedPageId = selectedPageId;
        this.listener = listener;
        this.showAddItem = showAddItem;
        this.vertical = vertical;
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
        configureMoveAccessibilityActions(holder);
        return holder;
    }

    @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof AddHolder) {
            TextView label = holder.itemView.findViewById(R.id.text_editor_add_page_label);
            label.setVisibility(holder.itemView.getResources().getBoolean(
                    R.bool.editor_add_button_show_text
            ) ? View.VISIBLE : View.GONE);
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

    private void configureMoveAccessibilityActions(PageHolder holder) {
        ViewCompat.setAccessibilityDelegate(holder.itemView, new AccessibilityDelegateCompat() {
            @Override
            public void onInitializeAccessibilityNodeInfo(
                    @NonNull View host,
                    @NonNull AccessibilityNodeInfoCompat info
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                int position = holder.getBindingAdapterPosition();
                if (!actionsEnabled || position == RecyclerView.NO_POSITION) {
                    return;
                }
                for (PageReorderAccessibilityModel.Direction direction
                        : PageReorderAccessibilityModel.availableActions(
                                vertical,
                                position,
                                pages.size()
                        )) {
                    info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                            actionId(direction),
                            host.getContext().getString(actionLabel(direction))
                    ));
                }
            }

            @Override
            public boolean performAccessibilityAction(
                    @NonNull View host,
                    int action,
                    Bundle args
            ) {
                PageReorderAccessibilityModel.Direction direction = directionForAction(action);
                int fromPosition = holder.getBindingAdapterPosition();
                if (!actionsEnabled
                        || direction == null
                        || fromPosition == RecyclerView.NO_POSITION
                        || !PageReorderAccessibilityModel.availableActions(
                                vertical,
                                fromPosition,
                                pages.size()
                        ).contains(direction)) {
                    return super.performAccessibilityAction(host, action, args);
                }
                long pageId = holder.getItemId();
                int toPosition = PageReorderAccessibilityModel.targetPosition(
                        direction,
                        fromPosition
                );
                if (!listener.onPageMoveRequested(fromPosition, toPosition)) {
                    return false;
                }
                restoreAccessibilityFocus(host, pageId, toPosition);
                return true;
            }
        });
    }

    private void restoreAccessibilityFocus(View host, long pageId, int position) {
        if (!(host.getParent() instanceof RecyclerView)) {
            return;
        }
        RecyclerView recyclerView = (RecyclerView) host.getParent();
        recyclerView.post(() -> {
            RecyclerView.ViewHolder movedHolder = recyclerView.findViewHolderForItemId(pageId);
            if (movedHolder == null) {
                return;
            }
            View movedPage = movedHolder.itemView;
            ViewCompat.performAccessibilityAction(
                    movedPage,
                    AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS,
                    null
            );
            movedPage.announceForAccessibility(movedPage.getContext().getString(
                    R.string.page_position_announcement,
                    PageReorderAccessibilityModel.pageNumberForPosition(position),
                    pages.size()
            ));
        });
    }

    private static int actionId(PageReorderAccessibilityModel.Direction direction) {
        switch (direction) {
            case LEFT:
                return R.id.accessibility_action_move_page_left;
            case RIGHT:
                return R.id.accessibility_action_move_page_right;
            case UP:
                return R.id.accessibility_action_move_page_up;
            case DOWN:
                return R.id.accessibility_action_move_page_down;
            default:
                throw new IllegalArgumentException("Unsupported direction: " + direction);
        }
    }

    private static int actionLabel(PageReorderAccessibilityModel.Direction direction) {
        switch (direction) {
            case LEFT:
                return R.string.action_move_page_left;
            case RIGHT:
                return R.string.action_move_page_right;
            case UP:
                return R.string.action_move_page_up;
            case DOWN:
                return R.string.action_move_page_down;
            default:
                throw new IllegalArgumentException("Unsupported direction: " + direction);
        }
    }

    private static PageReorderAccessibilityModel.Direction directionForAction(int action) {
        if (action == R.id.accessibility_action_move_page_left) {
            return PageReorderAccessibilityModel.Direction.LEFT;
        }
        if (action == R.id.accessibility_action_move_page_right) {
            return PageReorderAccessibilityModel.Direction.RIGHT;
        }
        if (action == R.id.accessibility_action_move_page_up) {
            return PageReorderAccessibilityModel.Direction.UP;
        }
        if (action == R.id.accessibility_action_move_page_down) {
            return PageReorderAccessibilityModel.Direction.DOWN;
        }
        return null;
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
        boolean onPageMoveRequested(int fromPosition, int toPosition);
    }
    private static final class PageHolder extends RecyclerView.ViewHolder {
        final ImageView thumbnail; final TextView number; String key;
        PageHolder(View view) { super(view); thumbnail = view.findViewById(R.id.image_page_edit_strip_thumbnail); number = view.findViewById(R.id.text_page_edit_strip_number); }
    }
    private static final class AddHolder extends RecyclerView.ViewHolder { AddHolder(View view) { super(view); } }
}
