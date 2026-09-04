package dev.vexsoft.core.paper.mob;

import org.bukkit.entity.Mob;

/** Applies plugin-owned entity-specific state after VexCore configures a mob carrier. */
@FunctionalInterface
public interface MobInitializer {

  /** Initializes one freshly spawned mob carrier. */
  void initialize(Mob mob);
}
