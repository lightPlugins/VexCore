package dev.vexsoft.core.paper.reactor.context;

/** Allows an effect to replace the damage of an active Paper event. */
public interface MutableDamageReactorContext extends DamageReactorContext {

  /** Replaces the event damage. */
  void setDamage(double damage);
}
