package dev.vexsoft.core.paper.packets.block;

import org.bukkit.block.data.BlockData;

/** Immutable client projection; the physical world is never modified. */
public record VirtualBlock(int x, int y, int z, BlockData original, BlockData replacement) {
    public VirtualBlock {
        original = original.clone();
        replacement = replacement.clone();
    }
}
