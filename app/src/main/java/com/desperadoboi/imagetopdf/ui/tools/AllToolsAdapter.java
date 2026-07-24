package com.desperadoboi.imagetopdf.ui.tools;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.desperadoboi.imagetopdf.R;

import java.util.Objects;

public final class AllToolsAdapter
        extends ListAdapter<AllToolsAdapter.Row, RecyclerView.ViewHolder> {
    public static final int VIEW_TYPE_SECTION = 0;
    public static final int VIEW_TYPE_TOOL = 1;

    private static final float AVAILABLE_ALPHA = 1f;
    private static final float DISABLED_ICON_ALPHA = 0.58f;

    private final OnToolSelectedListener listener;

    public AllToolsAdapter(OnToolSelectedListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
        setHasStableIds(true);
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).isSection() ? VIEW_TYPE_SECTION : VIEW_TYPE_TOOL;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_SECTION) {
            return new SectionViewHolder(inflater.inflate(
                    R.layout.item_tool_section_header,
                    parent,
                    false
            ));
        }
        return new ToolViewHolder(inflater.inflate(
                R.layout.item_catalog_tool,
                parent,
                false
        ));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = getItem(position);
        if (holder instanceof SectionViewHolder) {
            ((SectionViewHolder) holder).title.setText(row.getSectionTitleResId());
            return;
        }
        bindTool((ToolViewHolder) holder, row.getTool());
    }

    @Override
    public long getItemId(int position) {
        Row row = getItem(position);
        if (row.isSection()) {
            return Long.MIN_VALUE + row.getSectionTitleResId();
        }
        return row.getTool().getId().ordinal();
    }

    private void bindTool(ToolViewHolder holder, ToolDefinition definition) {
        boolean available = definition.isAvailable();
        CharSequence title = holder.itemView.getContext().getText(definition.getTitleResId());
        holder.icon.setImageResource(definition.getIconResId());
        holder.title.setText(title);
        int descriptionResId = getDescriptionResId(definition.getId());
        holder.badge.setVisibility(available ? View.GONE : View.VISIBLE);
        holder.icon.setAlpha(available ? AVAILABLE_ALPHA : DISABLED_ICON_ALPHA);
        holder.title.setEnabled(available);
        holder.title.setAlpha(AVAILABLE_ALPHA);
        holder.badge.setAlpha(AVAILABLE_ALPHA);
        holder.itemView.setEnabled(available);
        holder.itemView.setClickable(available);
        CharSequence availableDescription = descriptionResId != 0
                ? holder.itemView.getContext().getString(
                        R.string.tool_title_with_description,
                        title,
                        holder.itemView.getContext().getString(descriptionResId)
                )
                : title;
        holder.itemView.setContentDescription(available
                ? availableDescription
                : holder.itemView.getContext().getString(
                        R.string.catalog_tool_coming_soon_content_description,
                        title
                ));
        holder.itemView.setOnClickListener(available
                ? view -> listener.onToolSelected(definition.getId())
                : null);
    }

    private static int getDescriptionResId(ToolId toolId) {
        if (toolId == ToolId.DOCUMENT_VIEWER) {
            return R.string.tool_document_viewer_description;
        }
        if (toolId == ToolId.SMART_SCAN) {
            return R.string.tool_smart_scan_description;
        }
        return 0;
    }

    public interface OnToolSelectedListener {
        void onToolSelected(ToolId toolId);
    }

    public static final class Row {
        private final Integer sectionTitleResId;
        private final ToolDefinition tool;

        private Row(Integer sectionTitleResId, ToolDefinition tool) {
            this.sectionTitleResId = sectionTitleResId;
            this.tool = tool;
        }

        public static Row section(@StringRes int titleResId) {
            if (titleResId == 0) {
                throw new IllegalArgumentException("titleResId is required");
            }
            return new Row(titleResId, null);
        }

        public static Row tool(ToolDefinition definition) {
            return new Row(null, Objects.requireNonNull(definition, "tool is required"));
        }

        public boolean isSection() {
            return sectionTitleResId != null;
        }

        @StringRes
        int getSectionTitleResId() {
            if (sectionTitleResId == null) {
                throw new IllegalStateException("Row is not a section");
            }
            return sectionTitleResId;
        }

        ToolDefinition getTool() {
            if (tool == null) {
                throw new IllegalStateException("Row is not a tool");
            }
            return tool;
        }
    }

    static final class SectionViewHolder extends RecyclerView.ViewHolder {
        private final TextView title;

        SectionViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.text_tool_section_title);
        }
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

    private static final DiffUtil.ItemCallback<Row> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<Row>() {
                @Override
                public boolean areItemsTheSame(@NonNull Row oldItem, @NonNull Row newItem) {
                    if (oldItem.isSection() != newItem.isSection()) {
                        return false;
                    }
                    if (oldItem.isSection()) {
                        return oldItem.getSectionTitleResId() == newItem.getSectionTitleResId();
                    }
                    return oldItem.getTool().getId() == newItem.getTool().getId();
                }

                @Override
                public boolean areContentsTheSame(@NonNull Row oldItem, @NonNull Row newItem) {
                    if (oldItem.isSection()) {
                        return newItem.isSection()
                                && oldItem.getSectionTitleResId()
                                == newItem.getSectionTitleResId();
                    }
                    return !newItem.isSection()
                            && oldItem.getTool().equals(newItem.getTool());
                }
            };
}
