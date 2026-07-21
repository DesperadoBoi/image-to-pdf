package com.desperadoboi.imagetopdf.ui.tools;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

import java.util.Objects;

public final class ToolDefinition {
    public static final int NOT_ON_HOME = -1;

    private final ToolId id;
    private final int titleResId;
    private final int iconResId;
    private final ToolCategory category;
    private final ToolAvailability availability;
    private final boolean showOnHome;
    private final int homeOrder;
    private final int catalogOrder;

    public ToolDefinition(
            ToolId id,
            @StringRes int titleResId,
            @DrawableRes int iconResId,
            ToolCategory category,
            ToolAvailability availability,
            boolean showOnHome,
            int homeOrder,
            int catalogOrder
    ) {
        this.id = Objects.requireNonNull(id, "id is required");
        if (titleResId == 0) {
            throw new IllegalArgumentException("titleResId is required");
        }
        if (iconResId == 0) {
            throw new IllegalArgumentException("iconResId is required");
        }
        this.titleResId = titleResId;
        this.iconResId = iconResId;
        this.category = Objects.requireNonNull(category, "category is required");
        this.availability = Objects.requireNonNull(
                availability,
                "availability is required"
        );
        if (showOnHome && homeOrder < 0) {
            throw new IllegalArgumentException("Home tool requires a non-negative homeOrder");
        }
        if (!showOnHome && homeOrder != NOT_ON_HOME) {
            throw new IllegalArgumentException("Non-home tool must use NOT_ON_HOME");
        }
        if (catalogOrder < 0) {
            throw new IllegalArgumentException("catalogOrder must be non-negative");
        }
        this.showOnHome = showOnHome;
        this.homeOrder = homeOrder;
        this.catalogOrder = catalogOrder;
    }

    public ToolId getId() {
        return id;
    }

    @StringRes
    public int getTitleResId() {
        return titleResId;
    }

    @DrawableRes
    public int getIconResId() {
        return iconResId;
    }

    public ToolCategory getCategory() {
        return category;
    }

    public ToolAvailability getAvailability() {
        return availability;
    }

    public boolean isAvailable() {
        return availability == ToolAvailability.AVAILABLE;
    }

    public boolean shouldShowOnHome() {
        return showOnHome;
    }

    public int getHomeOrder() {
        return homeOrder;
    }

    public int getCatalogOrder() {
        return catalogOrder;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ToolDefinition)) {
            return false;
        }
        ToolDefinition definition = (ToolDefinition) other;
        return titleResId == definition.titleResId
                && iconResId == definition.iconResId
                && showOnHome == definition.showOnHome
                && homeOrder == definition.homeOrder
                && catalogOrder == definition.catalogOrder
                && id == definition.id
                && category == definition.category
                && availability == definition.availability;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
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
