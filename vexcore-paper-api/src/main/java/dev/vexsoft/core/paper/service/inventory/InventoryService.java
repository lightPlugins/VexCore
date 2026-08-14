package dev.vexsoft.core.paper.service.inventory;

import dev.vexsoft.core.paper.inventory.InventoryDefinition;
import dev.vexsoft.core.paper.inventory.InventoryKey;
import dev.vexsoft.core.paper.inventory.InventoryView;

import dev.vexsoft.core.api.service.registry.VexService;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.entity.Player;

/**
 * Registers inventory definitions and manages viewer inventory sessions
 */
public interface InventoryService extends VexService {

  /** Creates and registers an annotated inventory definition class */
  void register(Class<? extends InventoryDefinition> definitionType);

  /** Returns every inventory key registered by the current plugin */
  Collection<InventoryKey> getKeys();

  /** Opens a registered inventory and adds the current view to history */
  void open(Player player, InventoryKey key);

  /** Opens a view and adds the current view to history */
  void open(Player player, InventoryView view);

  /** Replaces the current view without changing its history */
  void replace(Player player, InventoryKey key);

  /** Replaces the current view without changing its history */
  void replace(Player player, InventoryView view);

  /** Refreshes the view currently open for the player */
  void refresh(Player player);

  /** Returns to the previous view or closes when no history remains */
  void back(Player player);

  /** Returns the requested number of views through the current history */
  void back(Player player, int steps);

  /** Returns to the nearest matching view in the current history */
  void backTo(Player player, InventoryKey key);

  /** Opens a fresh registered view and clears the current history */
  void openRoot(Player player, InventoryKey key);

  /** Opens a fresh dynamic view and clears the current history */
  void openRoot(Player player, InventoryView view);

  /** Closes the managed inventory and removes its session */
  void close(Player player);

  /** Returns the key currently open for the given viewer */
  Optional<InventoryKey> getCurrentInventory(UUID viewerId);

  /** Returns the view currently open for the given viewer */
  Optional<InventoryView> getCurrentView(UUID viewerId);
}
