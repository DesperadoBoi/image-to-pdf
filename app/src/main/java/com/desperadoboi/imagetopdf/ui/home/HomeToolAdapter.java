package com.desperadoboi.imagetopdf.ui.home;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.desperadoboi.imagetopdf.R;
import com.desperadoboi.imagetopdf.ui.tools.ToolDefinition;
import com.desperadoboi.imagetopdf.ui.tools.ToolId;

public final class HomeToolAdapter
        extends ListAdapter<ToolDefinition, HomeToolAdapter.ToolViewHolder> {
    private static final float AVAILABLE_ALPHA = 1f;
    private static final float COMING_SOON_ALPHA = 0.62f;

    private final OnToolSelectedListener listener;

    public HomeToolAdapter(OnToolSelectedListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
        setHasStableIds(true);
    }

    @NonNull
    @Override
    public ToolViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(
                R.layout.item_home_tool,
                parent,
                false
        );
        return new ToolViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ToolViewHolder holder, int position) {
        ToolDefinition definition = getItem(position);
        boolean available = definition.isAvailable();
        CharSequence title = holder.itemView.getContext().getText(definition.getTitleResId());

        holder.icon.setImageResource(definition.getIconResId());
        holder.title.setText(title);
        holder.badge.setVisibility(available ? View.GONE : View.VISIBLE);
        holder.icon.setAlpha(available ? AVAILABLE_ALPHA : COMING_SOON_ALPHA);
        holder.title.setAlpha(available ? AVAILABLE_ALPHA : COMING_SOON_ALPHA);
        holder.itemView.setEnabled(available);
        holder.itemView.setClickable(available);
        holder.itemView.setContentDescription(available
                ? title
                : holder.itemView.getContext().getString(
                        R.string.tool_coming_soon_content_description,
                        title
                ));
        holder.itemView.setOnClickListener(available
                ? view -> listener.onToolSelected(definition.getId())
                : null);
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).getId().ordinal();
    }

    public interface OnToolSelectedListener {
        void onToolSelected(ToolId toolId);
    }

    static final class ToolViewHolder extends RecyclerView.ViewHolder {
        private final AppCompatImageView icon;
        private final TextView title;
        private final TextView badge;

        ToolViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.image_tool_icon);
            title = itemView.findViewById(R.id.text_tool_title);
            badge = itemView.findViewById(R.id.text_tool_badge);
        }
    }

    private static final DiffUtil.ItemCallback<ToolDefinition> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<ToolDefinition>() {
                @Override
                public boolean areItemsTheSame(
                        @NonNull ToolDefinition oldItem,
                        @NonNull ToolDefinition newItem
                ) {
                    return oldItem.getId() == newItem.getId();
                }

                @Override
                public boolean areContentsTheSame(
                        @NonNull ToolDefinition oldItem,
                        @NonNull ToolDefinition newItem
                ) {
                    return oldItem.equals(newItem);
                }
            };
}
