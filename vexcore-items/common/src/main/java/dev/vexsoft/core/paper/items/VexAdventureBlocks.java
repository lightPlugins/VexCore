package dev.vexsoft.core.paper.items;

import java.util.Set;
import org.bukkit.NamespacedKey;

/** Physical block registry keys permitted by an Adventure item. An empty set permits no blocks. */
public record VexAdventureBlocks(Set<NamespacedKey> blocks) {

    /** Copies physical registry keys and rejects custom identities that require provider carrier resolution. */
    public VexAdventureBlocks {
        blocks = Set.copyOf(blocks);
        if (blocks.stream().anyMatch(key -> !key.getNamespace().equals("minecraft"))) {
            throw new IllegalArgumentException("Adventure predicates require physical Minecraft block keys");
        }
    }
}
