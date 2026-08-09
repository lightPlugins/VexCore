package dev.vexsoft.core.paper.reactor.context;

import dev.vexsoft.core.reactor.context.ReactorContext;
import org.bukkit.entity.Entity;

/** Exposes the target entity involved in a Paper reaction invocation. */
public interface TargetEntityReactorContext extends ReactorContext {

  /** Returns the target entity. */
  Entity getTarget();
}
