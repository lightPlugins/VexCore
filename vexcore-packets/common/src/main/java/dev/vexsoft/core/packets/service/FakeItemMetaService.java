package dev.vexsoft.core.packets.service;

import dev.vexsoft.core.api.service.VexService;
import dev.vexsoft.core.packets.item.FakeItemLoreMode;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

/**
 * Rewrites item names, lore and models for selected viewers without changing server items
 */
public interface FakeItemMetaService extends VexService {

  /** Replaces the lore of every matching item for all viewers */
  public default void setLore(
      final NamespacedKey itemIdKey,
      final String itemId,
      final List<Component> lore
  ) {
    setLore(itemIdKey, itemId, lore, FakeItemLoreMode.REPLACE);
  }

  /** Changes the lore of every matching item for all viewers */
  public void setLore(
      NamespacedKey itemIdKey,
      String itemId,
      List<Component> lore,
      FakeItemLoreMode mode
  );

  /** Replaces the lore of every matching item for one viewer */
  public default void setLore(
      final Player viewer,
      final NamespacedKey itemIdKey,
      final String itemId,
      final List<Component> lore
  ) {
    setLore(viewer, itemIdKey, itemId, lore, FakeItemLoreMode.REPLACE);
  }

  /** Changes the lore of every matching item for one viewer */
  public void setLore(
      Player viewer,
      NamespacedKey itemIdKey,
      String itemId,
      List<Component> lore,
      FakeItemLoreMode mode
  );

  /** Sets the fake display name for all matching items */
  public void setDisplayName(NamespacedKey itemIdKey, String itemId, Component displayName);

  /** Sets the fake display name for one viewer's matching items */
  public void setDisplayName(
      Player viewer,
      NamespacedKey itemIdKey,
      String itemId,
      Component displayName
  );

  /** Sets the fake item model for all matching items */
  public void setItemModel(NamespacedKey itemIdKey, String itemId, NamespacedKey itemModel);

  /** Sets the fake item model for one viewer's matching items */
  public void setItemModel(
      Player viewer,
      NamespacedKey itemIdKey,
      String itemId,
      NamespacedKey itemModel
  );

  /** Clears the global fake lore for matching items */
  public void clearLore(NamespacedKey itemIdKey, String itemId);

  /** Clears one viewer's fake lore for matching items */
  public void clearLore(Player viewer, NamespacedKey itemIdKey, String itemId);

  /** Clears the global fake display name for matching items */
  public void clearDisplayName(NamespacedKey itemIdKey, String itemId);

  /** Clears one viewer's fake display name for matching items */
  public void clearDisplayName(Player viewer, NamespacedKey itemIdKey, String itemId);

  /** Clears the global fake item model for matching items */
  public void clearItemModel(NamespacedKey itemIdKey, String itemId);

  /** Clears one viewer's fake item model for matching items */
  public void clearItemModel(Player viewer, NamespacedKey itemIdKey, String itemId);

  /** Clears every fake item metadata rule owned by this service */
  public void clearAll();

  /** Clears every fake item metadata rule owned by this service for one viewer */
  public void clearAll(Player viewer);

  /** Resends the current inventory contents to one viewer */
  public void refresh(Player viewer);

  /** Resends the current inventory contents to every online viewer */
  public void refreshAll();
}
