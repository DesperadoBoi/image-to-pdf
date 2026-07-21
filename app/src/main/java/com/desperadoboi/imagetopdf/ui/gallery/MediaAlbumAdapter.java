package com.desperadoboi.imagetopdf.ui.gallery;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.desperadoboi.imagetopdf.R;
import com.desperadoboi.imagetopdf.image.GalleryThumbnailLoader;
import com.desperadoboi.imagetopdf.model.MediaAlbum;
import com.desperadoboi.imagetopdf.model.MediaImage;

import java.util.ArrayList;
import java.util.List;

public final class MediaAlbumAdapter extends RecyclerView.Adapter<MediaAlbumAdapter.ViewHolder> {
    private final GalleryThumbnailLoader thumbnailLoader;
    private final Callback callback;
    private final ArrayList<AlbumRow> rows = new ArrayList<>();

    public MediaAlbumAdapter(GalleryThumbnailLoader thumbnailLoader, Callback callback) {
        this.thumbnailLoader = thumbnailLoader;
        this.callback = callback;
        setHasStableIds(true);
    }

    public void submit(List<MediaImage> images, List<MediaAlbum> albums, String allImagesName) {
        rows.clear();
        if (!images.isEmpty()) {
            rows.add(new AlbumRow(null, allImagesName, images.get(0).getUri(), images.size()));
        }
        for (MediaAlbum album : albums) {
            rows.add(new AlbumRow(
                    album.getBucketId(),
                    album.getDisplayName(),
                    album.getCoverUri(),
                    album.getImageCount()
            ));
        }
        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        String bucketId = rows.get(position).bucketId;
        return bucketId == null ? Long.MIN_VALUE + 1 : bucketId.hashCode();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(
                R.layout.item_media_album,
                parent,
                false
        );
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AlbumRow row = rows.get(position);
        holder.name.setText(row.displayName);
        holder.count.setText(holder.itemView.getResources().getQuantityString(
                R.plurals.gallery_album_image_count,
                row.imageCount,
                row.imageCount
        ));
        holder.itemView.setContentDescription(
                row.displayName + ", " + holder.count.getText()
        );
        holder.itemView.setOnClickListener(view -> callback.onAlbumClicked(
                row.bucketId,
                row.displayName
        ));
        int size = holder.itemView.getResources().getDimensionPixelSize(
                R.dimen.gallery_album_cover_size
        );
        String key = GalleryThumbnailLoader.buildKey(row.coverUri, size, size);
        if (key.equals(holder.boundKey)) {
            return;
        }
        holder.boundKey = key;
        releaseDisplayedBitmap(holder);
        thumbnailLoader.load(row.coverUri, size, size, new GalleryThumbnailLoader.Callback() {
            @Override
            public void onLoaded(String loadedKey, Bitmap bitmap) {
                if (!loadedKey.equals(holder.boundKey)) {
                    recycle(bitmap);
                    return;
                }
                releaseDisplayedBitmap(holder);
                holder.cover.setImageBitmap(bitmap);
            }

            @Override
            public void onError(String loadedKey) {
                if (loadedKey.equals(holder.boundKey)) {
                    holder.cover.setImageResource(R.drawable.ic_broken_image_24);
                }
            }
        });
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        holder.boundKey = null;
        releaseDisplayedBitmap(holder);
        super.onViewRecycled(holder);
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    private static void releaseDisplayedBitmap(ViewHolder holder) {
        if (holder.cover.getDrawable() instanceof BitmapDrawable) {
            recycle(((BitmapDrawable) holder.cover.getDrawable()).getBitmap());
        }
        holder.cover.setImageDrawable(null);
    }

    private static void recycle(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }

    public interface Callback {
        void onAlbumClicked(String bucketId, String displayName);
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView cover;
        private final TextView name;
        private final TextView count;
        private String boundKey;

        private ViewHolder(@NonNull View itemView) {
            super(itemView);
            cover = itemView.findViewById(R.id.image_media_album_cover);
            name = itemView.findViewById(R.id.text_media_album_name);
            count = itemView.findViewById(R.id.text_media_album_count);
        }
    }

    private static final class AlbumRow {
        private final String bucketId;
        private final String displayName;
        private final Uri coverUri;
        private final int imageCount;

        private AlbumRow(String bucketId, String displayName, Uri coverUri, int imageCount) {
            this.bucketId = bucketId;
            this.displayName = displayName;
            this.coverUri = coverUri;
            this.imageCount = imageCount;
        }
    }
}
