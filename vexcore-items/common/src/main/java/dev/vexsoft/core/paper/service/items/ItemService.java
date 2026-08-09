package dev.vexsoft.core.paper.service.items;

import dev.vexsoft.core.paper.items.ItemStackBuilder;

import dev.vexsoft.core.api.service.registry.VexService;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

/**
 * Creates component-based item builders and identifies Vex items
 */
public interface ItemService extends VexService {

  /** Creates a builder for one item with the default amount */
  default ItemStackBuilder builder(
      final NamespacedKey itemId,
      final Material material
  ) {
    return builder(itemId, material, 1);
  }

  /** Creates a builder for an item with the requested amount */
  ItemStackBuilder builder(NamespacedKey itemId, Material material, int amount);

  /** Creates a builder from a cloned item stack */
  ItemStackBuilder builder(NamespacedKey itemId, ItemStack itemStack);

  /** Reads the persistent Vex item identifier from an item stack */
  Optional<NamespacedKey> getItemId(ItemStack itemStack);

  /** Checks whether an item stack carries the requested Vex item identifier */
  boolean isItem(ItemStack itemStack, NamespacedKey itemId);
}
