package dev.vexsoft.core.paper.packets.item;

import dev.vexsoft.core.paper.packets.internal.FakeItemMetaRule;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.inventory.ItemStack;

/** Resolves viewer- and stack-specific packet presentation without mutating the source item. */
@FunctionalInterface
public interface FakeItemMetaResolver {

    /** Returns an optional presentation for one outgoing item stack. */
    Optional<FakeItemMetaRule> resolve(UUID viewerId, ItemStack item);
}
