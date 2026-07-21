package com.desperadoboi.imagetopdf.ui.tools;

import com.desperadoboi.imagetopdf.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

public final class ToolCatalog {
    private static final List<ToolDefinition> TOOLS = createTools();
    private static final List<ToolDefinition> HOME_TOOLS = createHomeTools();

    private ToolCatalog() {
    }

    public static List<ToolDefinition> getTools() {
        return TOOLS;
    }

    public static List<ToolDefinition> getHomeTools() {
        return HOME_TOOLS;
    }

    public static ToolDefinition get(ToolId id) {
        Objects.requireNonNull(id, "id is required");
        for (ToolDefinition definition : TOOLS) {
            if (definition.getId() == id) {
                return definition;
            }
        }
        throw new IllegalArgumentException("Unknown tool ID: " + id);
    }

    public static List<ToolDefinition> getByCategories(ToolCategory... categories) {
        if (categories == null || categories.length == 0) {
            return Collections.emptyList();
        }
        EnumSet<ToolCategory> requestedCategories = EnumSet.copyOf(Arrays.asList(categories));
        ArrayList<ToolDefinition> matches = new ArrayList<>();
        for (ToolDefinition definition : TOOLS) {
            if (requestedCategories.contains(definition.getCategory())) {
                matches.add(definition);
            }
        }
        matches.sort(Comparator.comparingInt(ToolDefinition::getCatalogOrder));
        return Collections.unmodifiableList(matches);
    }

    private static List<ToolDefinition> createTools() {
        List<ToolDefinition> tools = Arrays.asList(
                tool(ToolId.IMAGE_TO_PDF, R.string.tool_image_to_pdf,
                        R.drawable.ic_tool_image_to_pdf, ToolCategory.CREATE,
                        ToolAvailability.AVAILABLE, true, 0, 0),
                tool(ToolId.DOCX_TO_PDF, R.string.tool_docx_to_pdf,
                        R.drawable.ic_tool_docx_to_pdf, ToolCategory.CREATE,
                        ToolAvailability.COMING_SOON, false, ToolDefinition.NOT_ON_HOME, 1),
                tool(ToolId.PPT_TO_PDF, R.string.tool_ppt_to_pdf,
                        R.drawable.ic_tool_ppt_to_pdf, ToolCategory.CREATE,
                        ToolAvailability.COMING_SOON, false, ToolDefinition.NOT_ON_HOME, 2),
                tool(ToolId.PDF_TO_JPG, R.string.tool_pdf_to_jpg,
                        R.drawable.ic_tool_pdf_to_jpg, ToolCategory.CONVERT,
                        ToolAvailability.COMING_SOON, true, 4, 3),
                tool(ToolId.PDF_TO_WORD, R.string.tool_pdf_to_word,
                        R.drawable.ic_tool_pdf_to_word, ToolCategory.CONVERT,
                        ToolAvailability.COMING_SOON, false, ToolDefinition.NOT_ON_HOME, 4),
                tool(ToolId.PDF_TO_PPT, R.string.tool_pdf_to_ppt,
                        R.drawable.ic_tool_pdf_to_ppt, ToolCategory.CONVERT,
                        ToolAvailability.COMING_SOON, false, ToolDefinition.NOT_ON_HOME, 5),
                tool(ToolId.SMART_SCAN, R.string.tool_smart_scan,
                        R.drawable.ic_tool_smart_scan, ToolCategory.POPULAR,
                        ToolAvailability.AVAILABLE, true, 1, 6),
                tool(ToolId.ID_SCAN, R.string.tool_id_scan,
                        R.drawable.ic_tool_id_scan, ToolCategory.POPULAR,
                        ToolAvailability.COMING_SOON, false, ToolDefinition.NOT_ON_HOME, 7),
                tool(ToolId.IMPORT_PDF, R.string.tool_import_pdf,
                        R.drawable.ic_tool_import_pdf, ToolCategory.POPULAR,
                        ToolAvailability.COMING_SOON, true, 2, 8),
                tool(ToolId.PRINT_PDF, R.string.tool_print_pdf,
                        R.drawable.ic_tool_print_pdf, ToolCategory.POPULAR,
                        ToolAvailability.COMING_SOON, false, ToolDefinition.NOT_ON_HOME, 9),
                tool(ToolId.MERGE_PDF, R.string.tool_merge_pdf,
                        R.drawable.ic_tool_merge_pdf, ToolCategory.EDIT,
                        ToolAvailability.COMING_SOON, true, 5, 10),
                tool(ToolId.COMPRESS_PDF, R.string.tool_compress_pdf,
                        R.drawable.ic_tool_compress_pdf, ToolCategory.EDIT,
                        ToolAvailability.COMING_SOON, true, 3, 11),
                tool(ToolId.DRAW_ON_PDF, R.string.tool_draw_on_pdf,
                        R.drawable.ic_tool_draw_pdf, ToolCategory.EDIT,
                        ToolAvailability.COMING_SOON, false, ToolDefinition.NOT_ON_HOME, 12),
                tool(ToolId.ADD_TEXT, R.string.tool_add_text,
                        R.drawable.ic_tool_add_text, ToolCategory.EDIT,
                        ToolAvailability.COMING_SOON, false, ToolDefinition.NOT_ON_HOME, 13),
                tool(ToolId.SIGN_PDF, R.string.tool_sign_pdf,
                        R.drawable.ic_tool_signature, ToolCategory.EDIT,
                        ToolAvailability.COMING_SOON, false, ToolDefinition.NOT_ON_HOME, 14),
                tool(ToolId.LOCK_PDF, R.string.tool_lock_pdf,
                        R.drawable.ic_tool_lock_pdf, ToolCategory.SECURITY,
                        ToolAvailability.COMING_SOON, false, ToolDefinition.NOT_ON_HOME, 15),
                tool(ToolId.UNLOCK_PDF, R.string.tool_unlock_pdf,
                        R.drawable.ic_tool_unlock_pdf, ToolCategory.SECURITY,
                        ToolAvailability.COMING_SOON, false, ToolDefinition.NOT_ON_HOME, 16),
                tool(ToolId.CAMERA, R.string.tool_camera,
                        R.drawable.ic_tool_camera, ToolCategory.CREATE,
                        ToolAvailability.AVAILABLE, true, 6, 17),
                tool(ToolId.MORE, R.string.tool_more,
                        R.drawable.ic_tool_more, ToolCategory.POPULAR,
                        ToolAvailability.AVAILABLE, true, 7, 18)
        );
        return Collections.unmodifiableList(new ArrayList<>(tools));
    }

    private static List<ToolDefinition> createHomeTools() {
        ArrayList<ToolDefinition> homeTools = new ArrayList<>();
        for (ToolDefinition definition : TOOLS) {
            if (definition.shouldShowOnHome()) {
                homeTools.add(definition);
            }
        }
        homeTools.sort(Comparator.comparingInt(ToolDefinition::getHomeOrder));
        return Collections.unmodifiableList(homeTools);
    }

    private static ToolDefinition tool(
            ToolId id,
            int titleResId,
            int iconResId,
            ToolCategory category,
            ToolAvailability availability,
            boolean showOnHome,
            int homeOrder,
            int catalogOrder
    ) {
        return new ToolDefinition(
                id,
                titleResId,
                iconResId,
                category,
                availability,
                showOnHome,
                homeOrder,
                catalogOrder
        );
    }
}
