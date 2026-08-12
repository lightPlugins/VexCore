package dev.vexsoft.core.paper.level.menu;

import java.util.List;
import java.util.Objects;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Builds uniformly modelled menu entries on top of NAME_TAG items. */
public final class LevelMenuItems {

  private LevelMenuItems() {}

  /** Creates a localized level item while keeping the physical material fixed to NAME_TAG. */
  public static ItemStack create(
      final Key itemModel,
      final Component name,
      final List<Component> lore
  ) {
    ItemStack item = new ItemStack(Material.NAME_TAG);
    ItemMeta meta = item.getItemMeta();
    meta.displayName(Objects.requireNonNull(name, "name"));
    meta.lore(List.copyOf(Objects.requireNonNull(lore, "lore")));
    NamespacedKey model = NamespacedKey.fromString(
        Objects.requireNonNull(itemModel, "itemModel").asString()
    );
    if (model == null) {
      throw new IllegalArgumentException("Invalid item model: " + itemModel.asString());
    }
    meta.setItemModel(model);
    item.setItemMeta(meta);
    return item;
  }
}
