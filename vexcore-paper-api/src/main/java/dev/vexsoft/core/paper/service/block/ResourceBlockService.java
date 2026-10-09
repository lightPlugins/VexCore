package dev.vexsoft.core.paper.service.block;

import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.paper.block.ResourceBlock;
import dev.vexsoft.core.paper.block.ResourceBlockProvider;
import org.bukkit.block.Block;
import org.bukkit.NamespacedKey;
import java.util.Set;

/** Shared namespace resolution for globally placed Vanilla and custom resources. */
public interface ResourceBlockService extends VexService {

    /** Registers a custom namespace, preferring custom recognition over Vanilla. */
    AutoCloseable register(String namespace, ResourceBlockProvider provider);

    /** Captures the provider that owns the physical block. */
    ResourceBlock capture(Block block);

    /** Resolves native carrier block keys for an Adventure item's breaking predicates. */
    Set<NamespacedKey> adventureMaterials(ResourceBlock definition);

    /** Checks provider-specific attached plants or decorations before a support block is removed. */
    boolean isAttachedTo(Block block, Block support);

    /** Checks a block definition against its registered provider. */
    void validate(ResourceBlock definition);

    /** Places a block through its registered provider. */
    void place(Block block, ResourceBlock definition);
}
