package com.desperadoboi.imagetopdf.ui.gallery;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.desperadoboi.imagetopdf.R;
import com.desperadoboi.imagetopdf.image.GalleryThumbnailLoader;
import com.desperadoboi.imagetopdf.model.ImageSelection;
import com.desperadoboi.imagetopdf.model.MediaImage;

import java.util.ArrayList;
import java.util.List;

public final class GalleryImageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public enum Mode {
        RECENT,
        GRID
    }

    private static final int VIEW_TYPE_CAMERA = 1;
    private static final int VIEW_TYPE_IMAGE = 2;
    private static final String PAYLOAD_SELECTION = "gallery_selection";

    private final Mode mode;
    private final ImageSelection selection;
    private final GalleryThumbnailLoader thumbnailLoader;
    private final Callback callback;
    private final ArrayList<MediaImage> images = new ArrayList<>();
    private boolean includeCamera;

    public GalleryImageAdapter(
            Mode mode,
            ImageSelection selection,
            GalleryThumbnailLoader thumbnailLoader,
            Callback callback
    ) {
        this.mode = mode;
        this.selection = selection;
        this.thumbnailLoader = thumbnailLoader;
        this.callback = callback;
        setHasStableIds(true);
    }

    public void submit(List<MediaImage> newImages, boolean includeCamera) {
        images.clear();
        images.addAll(newImages);
        this.includeCamera = includeCamera;
        notifyDataSetChanged();
    }

    public void notifySelectionChanged() {
        if (getItemCount() > 0) {
            notifyItemRangeChanged(0, getItemCount(), PAYLOAD_SELECTION);
        }
    }

    @Override
    public long getItemId(int position) {
        if (isCameraPosition(position)) {
            return Long.MIN_VALUE;
        }
        return getImage(position).getId();
    }

    @Override
    public int getItemViewType(int position) {
        return isCameraPosition(position) ? VIEW_TYPE_CAMERA : VIEW_TYPE_IMAGE;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_CAMERA) {
            int layout = mode == Mode.RECENT
                    ? R.layout.item_gallery_camera_recent
                    : R.layout.item_gallery_camera_grid;
            return new CameraViewHolder(inflater.inflate(layout, parent, false));
        }
        int layout = mode == Mode.RECENT
                ? R.layout.item_gallery_image_recent
                : R.layout.item_gallery_image_grid;
        return new ImageViewHolder(inflater.inflate(layout, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof CameraViewHolder) {
            bindCamera((CameraViewHolder) holder);
        } else {
            bindImage((ImageViewHolder) holder, getImage(position), true);
        }
    }

    @Override
    public void onBindViewHolder(
            @NonNull RecyclerView.ViewHolder holder,
            int position,
            @NonNull List<Object> payloads
    ) {
        if (payloads.contains(PAYLOAD_SELECTION) && holder instanceof ImageViewHolder) {
            bindSelection((ImageViewHolder) holder, getImage(position));
            return;
        }
        onBindViewHolder(holder, position);
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        if (holder instanceof ImageViewHolder) {
            ImageViewHolder imageHolder = (ImageViewHolder) holder;
            imageHolder.boundKey = null;
            releaseDisplayedBitmap(imageHolder);
        }
        super.onViewRecycled(holder);
    }

    @Override
    public int getItemCount() {
        return images.size() + (includeCamera ? 1 : 0);
    }

    private void bindCamera(CameraViewHolder holder) {
        holder.root.setContentDescription(
                holder.root.getContext().getString(R.string.action_open_camera_content_description)
        );
        holder.root.setOnClickListener(view -> callback.onCameraRequested());
    }

    private void bindImage(ImageViewHolder holder, MediaImage image, boolean loadThumbnail) {
        bindSelection(holder, image);
        holder.root.setOnClickListener(view -> callback.onImageClicked(image));
        if (!loadThumbnail) {
            return;
        }
        int thumbnailSize = holder.root.getResources().getDimensionPixelSize(
                mode == Mode.RECENT
                        ? R.dimen.gallery_recent_item_size
                        : R.dimen.page_thumbnail_size
        );
        String key = GalleryThumbnailLoader.buildKey(image.getUri(), thumbnailSize, thumbnailSize);
        if (key.equals(holder.boundKey)) {
            return;
        }
        holder.boundKey = key;
        releaseDisplayedBitmap(holder);
        thumbnailLoader.load(
                image.getUri(),
                thumbnailSize,
                thumbnailSize,
                new GalleryThumbnailLoader.Callback() {
                    @Override
                    public void onLoaded(String loadedKey, Bitmap bitmap) {
                        if (!loadedKey.equals(holder.boundKey)) {
                            recycle(bitmap);
                            return;
                        }
                        releaseDisplayedBitmap(holder);
                        holder.image.setImageBitmap(bitmap);
                    }

                    @Override
                    public void onError(String loadedKey) {
                        if (loadedKey.equals(holder.boundKey)) {
                            holder.image.setImageResource(R.drawable.ic_broken_image_24);
                        }
                    }
                }
        );
    }

    private void bindSelection(ImageViewHolder holder, MediaImage image) {
        int selectionNumber = selection.getSelectionNumber(image.getUri());
        boolean selected = selectionNumber > 0;
        holder.root.setBackgroundResource(selected
                ? R.drawable.bg_gallery_thumbnail_selected
                : R.drawable.bg_gallery_thumbnail);
        holder.badge.setVisibility(selected ? View.VISIBLE : View.GONE);
        if (selected) {
            holder.badge.setText(String.valueOf(selectionNumber));
            holder.badge.setContentDescription(holder.badge.getContext().getString(
                    R.string.gallery_selected_badge_content_description,
                    selectionNumber
            ));
        }
        String name = image.getDisplayName().isEmpty()
                ? image.getUri().getLastPathSegment()
                : image.getDisplayName();
        if (name == null) {
            name = "";
        }
        holder.root.setContentDescription(holder.root.getContext().getString(
                selected
                        ? R.string.action_deselect_image_content_description
                        : R.string.action_select_image_content_description,
                name
        ));
    }

    private boolean isCameraPosition(int position) {
        return includeCamera && position == 0;
    }

    private MediaImage getImage(int position) {
        return images.get(position - (includeCamera ? 1 : 0));
    }

    private static void releaseDisplayedBitmap(ImageViewHolder holder) {
        if (holder.image.getDrawable() instanceof BitmapDrawable) {
            recycle(((BitmapDrawable) holder.image.getDrawable()).getBitmap());
        }
        holder.image.setImageDrawable(null);
    }

    private static void recycle(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }

    public interface Callback {
        void onImageClicked(MediaImage image);

        void onCameraRequested();
    }

    private static final class CameraViewHolder extends RecyclerView.ViewHolder {
        private final View root;

        private CameraViewHolder(@NonNull View itemView) {
            super(itemView);
            root = itemView.findViewById(R.id.layout_gallery_camera);
        }
    }

    private static final class ImageViewHolder extends RecyclerView.ViewHolder {
        private final FrameLayout root;
        private final android.widget.ImageView image;
        private final TextView badge;
        private String boundKey;

        private ImageViewHolder(@NonNull View itemView) {
            super(itemView);
            root = itemView.findViewById(R.id.layout_gallery_image);
            image = itemView.findViewById(R.id.image_gallery_thumbnail);
            badge = itemView.findViewById(R.id.text_gallery_selection_badge);
        }
    }
}
