package com.desperadoboi.imagetopdf.ui.tools;

import org.junit.Test;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ToolCatalogTest {
    @Test
    public void everyToolIdHasExactlyOneDefinition() {
        Set<ToolId> ids = new HashSet<>();

        for (ToolDefinition definition : ToolCatalog.getTools()) {
            assertTrue(ids.add(definition.getId()));
        }

        assertEquals(EnumSet.allOf(ToolId.class), ids);
    }

    @Test
    public void homeContainsExactlyEightDefinitionsWithUniqueOrder() {
        List<ToolDefinition> homeTools = ToolCatalog.getHomeTools();
        Set<Integer> orders = new HashSet<>();

        assertEquals(8, homeTools.size());
        for (ToolDefinition definition : homeTools) {
            assertTrue(definition.shouldShowOnHome());
            assertTrue(orders.add(definition.getHomeOrder()));
        }
    }

    @Test
    public void homeUsesExpectedOrder() {
        ToolId[] expected = {
                ToolId.IMAGE_TO_PDF,
                ToolId.SMART_SCAN,
                ToolId.IMPORT_PDF,
                ToolId.COMPRESS_PDF,
                ToolId.PDF_TO_JPG,
                ToolId.MERGE_PDF,
                ToolId.CAMERA,
                ToolId.MORE
        };

        for (int index = 0; index < expected.length; index++) {
            assertEquals(expected[index], ToolCatalog.getHomeTools().get(index).getId());
        }
    }

    @Test
    public void implementedHomeActionsAreAvailable() {
        assertEquals(ToolAvailability.AVAILABLE,
                ToolCatalog.get(ToolId.IMAGE_TO_PDF).getAvailability());
        assertEquals(ToolAvailability.AVAILABLE,
                ToolCatalog.get(ToolId.CAMERA).getAvailability());
        assertEquals(ToolAvailability.AVAILABLE,
                ToolCatalog.get(ToolId.MORE).getAvailability());
    }

    @Test
    public void plannedHomeToolsAreComingSoon() {
        assertEquals(ToolAvailability.COMING_SOON,
                ToolCatalog.get(ToolId.SMART_SCAN).getAvailability());
        assertEquals(ToolAvailability.COMING_SOON,
                ToolCatalog.get(ToolId.IMPORT_PDF).getAvailability());
        assertEquals(ToolAvailability.COMING_SOON,
                ToolCatalog.get(ToolId.COMPRESS_PDF).getAvailability());
    }

    @Test
    public void categoriesContainExpectedTools() {
        assertCategoryContains(ToolCategory.CREATE,
                ToolId.IMAGE_TO_PDF, ToolId.DOCX_TO_PDF, ToolId.PPT_TO_PDF, ToolId.CAMERA);
        assertCategoryContains(ToolCategory.CONVERT,
                ToolId.PDF_TO_JPG, ToolId.PDF_TO_WORD, ToolId.PDF_TO_PPT);
        assertCategoryContains(ToolCategory.POPULAR,
                ToolId.SMART_SCAN, ToolId.ID_SCAN, ToolId.IMPORT_PDF,
                ToolId.PRINT_PDF, ToolId.MORE);
        assertCategoryContains(ToolCategory.EDIT,
                ToolId.MERGE_PDF, ToolId.COMPRESS_PDF, ToolId.DRAW_ON_PDF,
                ToolId.ADD_TEXT, ToolId.SIGN_PDF);
        assertCategoryContains(ToolCategory.SECURITY,
                ToolId.LOCK_PDF, ToolId.UNLOCK_PDF);
    }

    @Test
    public void everyDefinitionHasResourcesAndNoNullValues() {
        for (ToolDefinition definition : ToolCatalog.getTools()) {
            assertNotNull(definition);
            assertNotNull(definition.getId());
            assertNotNull(definition.getCategory());
            assertNotNull(definition.getAvailability());
            assertTrue(definition.getTitleResId() != 0);
            assertTrue(definition.getIconResId() != 0);
        }
    }

    @Test
    public void everyHomeToolHasDrawableResource() {
        for (ToolDefinition definition : ToolCatalog.getHomeTools()) {
            assertTrue(definition.getIconResId() != 0);
        }
    }

    @Test
    public void exposedListsAreImmutable() {
        assertThrows(
                UnsupportedOperationException.class,
                () -> ToolCatalog.getTools().clear()
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> ToolCatalog.getHomeTools().clear()
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> ToolCatalog.getByCategories(ToolCategory.EDIT).clear()
        );
    }

    @Test
    public void missingCategoryRequestReturnsImmutableEmptyList() {
        List<ToolDefinition> tools = ToolCatalog.getByCategories();

        assertTrue(tools.isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> tools.add(
                ToolCatalog.get(ToolId.IMAGE_TO_PDF)
        ));
    }

    private void assertCategoryContains(ToolCategory category, ToolId... expectedIds) {
        Set<ToolId> actualIds = new HashSet<>();
        for (ToolDefinition definition : ToolCatalog.getByCategories(category)) {
            actualIds.add(definition.getId());
        }
        for (ToolId expectedId : expectedIds) {
            assertTrue(actualIds.contains(expectedId));
        }
        assertFalse(actualIds.isEmpty());
    }
}
