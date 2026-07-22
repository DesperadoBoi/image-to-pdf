package com.desperadoboi.imagetopdf.ui.tools;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.desperadoboi.imagetopdf.R;

import java.util.ArrayList;
import java.util.List;

public final class AllToolsFragment extends Fragment {
    public static final String TAG = "AllToolsFragment";
    public static final String RESULT_TOOL_REQUEST = "all_tools_result_tool_request";
    public static final String RESULT_TOOL_ID = "tool_id";

    private static final int COLUMN_COUNT = 3;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_all_tools, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.findViewById(R.id.button_all_tools_back).setOnClickListener(
                ignored -> closeCatalog()
        );

        AllToolsAdapter adapter = new AllToolsAdapter(this::handleToolSelected);
        GridLayoutManager layoutManager = new GridLayoutManager(requireContext(), COLUMN_COUNT);
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return adapter.getItemViewType(position) == AllToolsAdapter.VIEW_TYPE_SECTION
                        ? COLUMN_COUNT
                        : 1;
            }
        });
        RecyclerView recyclerView = view.findViewById(R.id.recycler_all_tools);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);
        adapter.submitList(buildRows());
    }

    private List<AllToolsAdapter.Row> buildRows() {
        ArrayList<AllToolsAdapter.Row> rows = new ArrayList<>();
        addSection(
                rows,
                R.string.tool_category_create_convert,
                ToolCategory.CREATE,
                ToolCategory.CONVERT
        );
        addSection(rows, R.string.tool_category_popular, ToolCategory.POPULAR);
        addSection(rows, R.string.tool_category_edit, ToolCategory.EDIT);
        addSection(rows, R.string.tool_category_security, ToolCategory.SECURITY);
        return rows;
    }

    private void addSection(
            List<AllToolsAdapter.Row> rows,
            int titleResId,
            ToolCategory... categories
    ) {
        rows.add(AllToolsAdapter.Row.section(titleResId));
        for (ToolDefinition definition : ToolCatalog.getByCategories(categories)) {
            if (definition.getId() != ToolId.CAMERA && definition.getId() != ToolId.MORE) {
                rows.add(AllToolsAdapter.Row.tool(definition));
            }
        }
    }

    private void handleToolSelected(ToolId toolId) {
        if ((toolId != ToolId.IMAGE_TO_PDF
                && toolId != ToolId.SMART_SCAN
                && toolId != ToolId.DOCUMENT_VIEWER)
                || !ToolCatalog.get(toolId).isAvailable()) {
            return;
        }
        Bundle result = new Bundle();
        result.putString(RESULT_TOOL_ID, toolId.name());
        getParentFragmentManager().setFragmentResult(RESULT_TOOL_REQUEST, result);
        closeCatalog();
    }

    private void closeCatalog() {
        getParentFragmentManager().popBackStack();
    }
}
