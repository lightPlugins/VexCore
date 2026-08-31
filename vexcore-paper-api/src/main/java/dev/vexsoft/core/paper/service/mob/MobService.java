package dev.vexsoft.core.paper.service.mob;

import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.paper.mob.MobDamageResult;
import dev.vexsoft.core.paper.mob.MobHandle;
import dev.vexsoft.core.paper.mob.MobRemovalReason;
import dev.vexsoft.core.paper.mob.MobSnapshot;
import dev.vexsoft.core.paper.mob.MobSpawnRequest;
import dev.vexsoft.core.paper.packets.display.DisplayGlowColor;
import java.util.Collection;
import java.util.Optional;
import org.bukkit.entity.Entity;

/** Owner-scoped lifecycle and state access for non-persistent custom mobs. */
public interface MobService extends VexService {

  /** Spawns one runtime mob from an owned definition. */
  MobHandle spawn(MobSpawnRequest request);

  /** Finds a runtime mob by its handle. */
  Optional<MobSnapshot> find(MobHandle handle);

  /** Finds a runtime mob represented by a Bukkit entity. */
  Optional<MobSnapshot> find(Entity entity);

  /** Returns every active runtime mob owned by this service scope. */
  Collection<MobSnapshot> getActiveMobs();

  /** Applies positive custom damage and removes the carrier on a lethal hit. */
  MobDamageResult damage(MobHandle handle, double amount);

  /** Sets custom health within zero and maximum health. */
  MobSnapshot setHealth(MobHandle handle, double health);

  /** Updates the native scale and refreshes dependent presentation. */
  MobSnapshot setScale(MobHandle handle, double scale);

  /** Sets or replaces the viewer-specific default glow. */
  MobSnapshot setGlow(MobHandle handle, DisplayGlowColor color);

  /** Removes the viewer-specific default glow. */
  MobSnapshot clearGlow(MobHandle handle);

  /** Re-renders every visible viewer's hologram and glow. */
  void refreshPresentation(MobHandle handle);

  /** Removes one runtime mob for the supplied lifecycle reason. */
  boolean remove(MobHandle handle, MobRemovalReason reason);

  /** Removes every active runtime mob owned by this service scope. */
  int removeAll(MobRemovalReason reason);
}
