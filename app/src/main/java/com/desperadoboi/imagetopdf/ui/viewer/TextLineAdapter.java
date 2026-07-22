package com.desperadoboi.imagetopdf.ui.viewer;

import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.desperadoboi.imagetopdf.R;

import java.util.Collections;
import java.util.List;

final class TextLineAdapter extends RecyclerView.Adapter<TextLineAdapter.ViewHolder> {
    private List<String> lines = Collections.emptyList();

    void submit(List<String> lines) {
        int previousCount = this.lines.size();
        this.lines = lines == null ? Collections.emptyList() : lines;
        if (previousCount > 0) notifyItemRangeRemoved(0, previousCount);
        if (!this.lines.isEmpty()) notifyItemRangeInserted(0, this.lines.size());
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        TextView textView = new TextView(parent.getContext());
        int horizontal = parent.getResources().getDimensionPixelSize(
                R.dimen.viewer_text_horizontal_padding
        );
        int vertical = parent.getResources().getDimensionPixelSize(
                R.dimen.viewer_text_vertical_padding
        );
        textView.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        textView.setPadding(horizontal, vertical, horizontal, vertical);
        textView.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        textView.setTextColor(ContextCompat.getColor(
                textView.getContext(),
                R.color.viewer_document_text
        ));
        textView.setTextIsSelectable(true);
        textView.setHorizontallyScrolling(false);
        return new ViewHolder(textView);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.textView.setText(lines.get(position));
    }

    @Override
    public int getItemCount() {
        return lines.size();
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView textView;

        ViewHolder(TextView textView) {
            super(textView);
            this.textView = textView;
        }
    }
}
