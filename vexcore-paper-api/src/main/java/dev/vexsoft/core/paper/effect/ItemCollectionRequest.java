package dev.vexsoft.core.paper.effect;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

/** Cosmetic collection flight; the caller has already committed its rewards before submitting this request. */
public record ItemCollectionRequest(
    Location origin, ItemStack item, BooleanSupplier active, CollectionBlockView blocks, int color, int accentColor
) {

    /** Copies mutable inputs; active is checked only on the viewer's entity scheduler. */
    public ItemCollectionRequest {
        origin = Objects.requireNonNull(origin, "origin").clone();
        Objects.requireNonNull(origin.getWorld(), "origin world");
        item = Objects.requireNonNull(item, "item").clone();
        Objects.requireNonNull(active, "active");
        Objects.requireNonNull(blocks, "blocks");
        if (item.isEmpty() || (color & 0xFF000000) != 0 || (accentColor & 0xFF000000) != 0) {
            throw new IllegalArgumentException("Collection effects need an item and RGB colors");
        }
        item.setAmount(1);
    }

    /** Uses the shared violet and pale-lavender collection palette. */
    public ItemCollectionRequest(Location origin, ItemStack item, BooleanSupplier active, CollectionBlockView blocks) {
        this(origin, item, active, blocks, 0x8B5CF6, 0xDDD6FE);
    }

    @Override
    public Location origin() {
        return origin.clone();
    }

    @Override
    public ItemStack item() {
        return item.clone();
    }
}
