package dev.vexsoft.core.item;

import dev.vexsoft.core.api.service.VexService;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

/**
 * Creates component-based item builders and identifies Vex items
 */
public interface ItemService extends VexService {

  /** Creates a builder for one item with the default amount */
  public default ItemStackBuilder builder(
      final NamespacedKey itemId,
      final Material material
  ) {
    return builder(itemId, material, 1);
  }

  /** Creates a builder for an item with the requested amount */
  public ItemStackBuilder builder(NamespacedKey itemId, Material material, int amount);

  /** Creates a builder from a cloned item stack */
  public ItemStackBuilder builder(NamespacedKey itemId, ItemStack itemStack);

  /** Reads the persistent Vex item identifier from an item stack */
  public Optional<NamespacedKey> getItemId(ItemStack itemStack);

  /** Checks whether an item stack carries the requested Vex item identifier */
  public boolean isItem(ItemStack itemStack, NamespacedKey itemId);
}
