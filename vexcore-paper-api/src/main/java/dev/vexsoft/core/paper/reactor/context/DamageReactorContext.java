package dev.vexsoft.core.paper.reactor.context;

import dev.vexsoft.core.reactor.context.ReactorContext;
import org.bukkit.event.entity.EntityDamageEvent;

/** Exposes damage information associated with a Paper reaction invocation. */
public interface DamageReactorContext extends ReactorContext {

  /** Returns the current damage value. */
  double getDamage();

  /** Returns the Paper damage cause. */
  EntityDamageEvent.DamageCause getDamageCause();
}
