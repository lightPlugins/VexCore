package dev.vexsoft.core.packets.internal;

import java.util.Optional;
import java.util.UUID;
import org.bukkit.inventory.ItemStack;

/**
 * Resolves fake metadata rules for Bukkit item stacks
 */
public interface FakeItemMetaLookup {

  /** Finds the effective fake metadata rule for one viewer and item */
  public Optional<FakeItemMetaRule> find(UUID viewerId, ItemStack itemStack);

  /** Checks whether any rule can affect packets for the viewer */
  public boolean hasAny(UUID viewerId);
}
