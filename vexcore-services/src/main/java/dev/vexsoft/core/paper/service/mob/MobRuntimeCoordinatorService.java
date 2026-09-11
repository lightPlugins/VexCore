package dev.vexsoft.core.paper.service.mob;

import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.paper.mob.MobDamageResult;
import dev.vexsoft.core.paper.mob.MobDefinition;
import dev.vexsoft.core.paper.mob.MobHandle;
import dev.vexsoft.core.paper.mob.MobKey;
import dev.vexsoft.core.paper.mob.MobRemovalReason;
import dev.vexsoft.core.paper.mob.MobSnapshot;
import dev.vexsoft.core.paper.mob.MobSpawnRequest;
import dev.vexsoft.core.paper.packets.display.DisplayGlowColor;
import java.util.Collection;
import java.util.Optional;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

/** Coordinates all live, non-persistent custom mob instances. */
public interface MobRuntimeCoordinatorService extends VexService {

  /** Spawns a runtime mob from an owner-registered definition. */
  MobHandle spawn(ServiceOwner owner, MobDefinition definition, MobSpawnRequest request);

  /** Finds an active mob by owner and runtime handle. */
  Optional<MobSnapshot> find(ServiceOwner owner, MobHandle handle);

  /** Finds an active custom mob represented by a Bukkit entity. */
  Optional<MobSnapshot> find(Entity entity);

  /** Returns immutable snapshots of every active mob belonging to the owner. */
  Collection<MobSnapshot> getActiveMobs(ServiceOwner owner);

  /** Applies positive custom damage to an owner-scoped runtime mob. */
  MobDamageResult damage(ServiceOwner owner, MobHandle handle, double amount);

  /** Sets bounded custom health and removes the mob when health reaches zero. */
  MobSnapshot setHealth(ServiceOwner owner, MobHandle handle, double health);

  /** Updates native scale and all dependent viewer presentation. */
  MobSnapshot setScale(ServiceOwner owner, MobHandle handle, double scale);

  /** Applies a finite velocity without exposing the native carrier. */
  MobSnapshot setVelocity(ServiceOwner owner, MobHandle handle, Vector velocity);

  /** Sets or replaces the viewer-specific default glow. */
  MobSnapshot setGlow(ServiceOwner owner, MobHandle handle, DisplayGlowColor color);

  /** Removes the viewer-specific default glow. */
  MobSnapshot clearGlow(ServiceOwner owner, MobHandle handle);

  /** Re-renders hologram and glow presentation for every current viewer. */
  void refreshPresentation(ServiceOwner owner, MobHandle handle);

  /** Removes one runtime mob and emits its lifecycle notification. */
  boolean remove(ServiceOwner owner, MobHandle handle, MobRemovalReason reason);

  /** Schedules owner-thread-safe removal with an invalid-entity fallback. */
  void removeSafely(ServiceOwner owner, MobHandle handle, MobRemovalReason reason);

  /** Removes every active runtime mob owned by the supplied service owner. */
  int removeAll(ServiceOwner owner, MobRemovalReason reason);

  /** Removes every active owner mob created from one definition. */
  int removeDefinition(ServiceOwner owner, MobKey key);

  /** Registers an exactly-once runtime removal listener. */
  void addRemovalListener(MobRuntimeRemovalListener listener);

  /** Unregisters a runtime removal listener. */
  void removeRemovalListener(MobRuntimeRemovalListener listener);

  /** Registers platform listeners and accepts runtime work. */
  void start();

  /** Removes live mobs and unregisters platform listeners. */
  void shutdown();
}
