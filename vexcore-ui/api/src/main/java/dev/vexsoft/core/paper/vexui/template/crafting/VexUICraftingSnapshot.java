package dev.vexsoft.core.paper.vexui.template.crafting;

import dev.vexsoft.core.paper.vexui.template.VexUIIcon;
import java.util.List;
import java.util.Objects;
import net.kyori.adventure.text.Component;

/** Station metadata; recipe presentation is requested lazily for the visible page only. */
public record VexUICraftingSnapshot(boolean valid, Component title, List<Category> categories) {

    /** Copies ordered categories and rejects ambiguous navigation identities. */
    public VexUICraftingSnapshot {
        Objects.requireNonNull(title, "title");
        categories = List.copyOf(categories);
        if (categories.stream().map(Category::id).distinct().count() != categories.size()) {
            throw new IllegalArgumentException("Duplicate crafting category");
        }
    }

    /** Ordered recipe identities avoid calculating costs for hidden pages and categories. */
    public record Category(String id, Component name, Component tooltip, List<String> recipes) {

        /** Copies recipe identities and validates stable keys. */
        public Category {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(tooltip, "tooltip");
            recipes = List.copyOf(recipes);
            if (id.isBlank() || recipes.stream().anyMatch(String::isBlank)
                || recipes.stream().distinct().count() != recipes.size()) {
                throw new IllegalArgumentException("Invalid crafting category");
            }
        }
    }

    /** One visible result preview; even locked recipes can be selected to inspect requirements. */
    public record Recipe(Component name, Component status, Component tooltip, VexUIIcon icon) {

        /** Requires complete visible presentation. */
        public Recipe {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(tooltip, "tooltip");
            Objects.requireNonNull(icon, "icon");
        }
    }
}
