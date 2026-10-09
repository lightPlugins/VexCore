package dev.vexsoft.core.paper.block;

import org.bukkit.block.Block;
import org.bukkit.NamespacedKey;
import java.util.Set;

/** Adapter for Vanilla or custom block providers; all world calls require the owning server thread. */
public interface ResourceBlockProvider {

    /** Returns whether the provider recognizes this physical block. */
    boolean recognizes(Block block);

    /** Captures stable identity and properties without a player-specific projection. */
    ResourceBlock capture(Block block);

    /** Physical carrier keys for native Adventure predicates; custom adapters must supply their carriers. */
    default Set<NamespacedKey> adventureMaterials(final ResourceBlock definition) {
        return Set.of();
    }

    /** Returns whether a block must be harvested together with the supporting column segment. */
    default boolean isAttachedTo(final Block block, final Block support) {
        return false;
    }

    /** Rejects unsupported keys or property values before a configuration is published. */
    void validate(ResourceBlock definition);

    /** Places the exact definition with controlled physics. */
    void place(Block block, ResourceBlock definition);
}
