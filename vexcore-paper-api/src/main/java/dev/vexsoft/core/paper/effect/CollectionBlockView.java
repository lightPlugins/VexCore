package dev.vexsoft.core.paper.effect;

import org.bukkit.block.data.BlockData;

/** Supplies viewer-specific block overrides without reading live world state from another region. */
@FunctionalInterface
public interface CollectionBlockView {

    /** Returns a snapshot override, or null to use the physical block; must be safe on any region thread. */
    BlockData find(int x, int y, int z);
}
