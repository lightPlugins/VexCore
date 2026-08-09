package dev.vexsoft.core.paper.packets.service;

import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.paper.packets.item.FakeItemLoreMode;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

/**
 * Rewrites item names, lore and models for selected viewers without changing server items
 */
public interface FakeItemMetaService extends VexService {

  /** Replaces the lore of every matching item for all viewers */
  default void setLore(
      final NamespacedKey itemIdKey,
      final String itemId,
      final List<Component> lore
  ) {
    setLore(itemIdKey, itemId, lore, FakeItemLoreMode.REPLACE);
  }

  /** Changes the lore of every matching item for all viewers */
  void setLore(
      NamespacedKey itemIdKey,
      String itemId,
      List<Component> lore,
      FakeItemLoreMode mode
  );

  /** Replaces the lore of every matching item for one viewer */
  default void setLore(
      final Player viewer,
      final NamespacedKey itemIdKey,
      final String itemId,
      final List<Component> lore
  ) {
    setLore(viewer, itemIdKey, itemId, lore, FakeItemLoreMode.REPLACE);
  }

  /** Changes the lore of every matching item for one viewer */
  void setLore(
      Player viewer,
      NamespacedKey itemIdKey,
      String itemId,
      List<Component> lore,
      FakeItemLoreMode mode
  );

  /** Sets the fake display name for all matching items */
  void setDisplayName(NamespacedKey itemIdKey, String itemId, Component displayName);

  /** Sets the fake display name for one viewer's matching items */
  void setDisplayName(
      Player viewer,
      NamespacedKey itemIdKey,
      String itemId,
      Component displayName
  );

  /** Sets the fake item model for all matching items */
  void setItemModel(NamespacedKey itemIdKey, String itemId, NamespacedKey itemModel);

  /** Sets the fake item model for one viewer's matching items */
  void setItemModel(
      Player viewer,
      NamespacedKey itemIdKey,
      String itemId,
      NamespacedKey itemModel
  );

  /** Clears the global fake lore for matching items */
  void clearLore(NamespacedKey itemIdKey, String itemId);

  /** Clears one viewer's fake lore for matching items */
  void clearLore(Player viewer, NamespacedKey itemIdKey, String itemId);

  /** Clears the global fake display name for matching items */
  void clearDisplayName(NamespacedKey itemIdKey, String itemId);

  /** Clears one viewer's fake display name for matching items */
  void clearDisplayName(Player viewer, NamespacedKey itemIdKey, String itemId);

  /** Clears the global fake item model for matching items */
  void clearItemModel(NamespacedKey itemIdKey, String itemId);

  /** Clears one viewer's fake item model for matching items */
  void clearItemModel(Player viewer, NamespacedKey itemIdKey, String itemId);

  /** Clears every fake item metadata rule owned by this service */
  void clearAll();

  /** Clears every fake item metadata rule owned by this service for one viewer */
  void clearAll(Player viewer);

  /** Resends the current inventory contents to one viewer */
  void refresh(Player viewer);

  /** Resends the current inventory contents to every online viewer */
  void refreshAll();
}
