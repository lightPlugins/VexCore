package dev.vexsoft.core.paper.packets.service;

import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.paper.packets.display.FakeDisplayHandle;
import dev.vexsoft.core.paper.packets.dummy.BobRotation;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Owns viewer-specific player previews and their animation state. */
public interface PlayerDummyService extends VexService {
  /** Position is the body center; the viewer supplies the skin. */
  FakeDisplayHandle spawn(Player viewer, Location center, BobRotation animation);

  /** Boots, leggings, chestplate, helmet, as in PlayerInventory.getArmorContents(). */
  void armor(FakeDisplayHandle handle, ItemStack[] armor);

  /** Advances animation from a caller's existing viewer-bound visual loop. */
  void animate(FakeDisplayHandle handle);

  /** Returns whether the handle still belongs to a live preview. */
  boolean isActive(FakeDisplayHandle handle);

  /** Removes the identified visual and releases its runtime state. */
  void remove(FakeDisplayHandle handle);

  /** Removes every dummy owned by the specified viewer. */
  void removeAll(UUID viewerId);
}
