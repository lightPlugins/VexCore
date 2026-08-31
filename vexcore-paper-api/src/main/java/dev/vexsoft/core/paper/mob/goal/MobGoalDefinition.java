package dev.vexsoft.core.paper.mob.goal;

/** Marker for an immutable opt-in custom mob goal definition. */
public sealed interface MobGoalDefinition
    permits RandomMovementGoalDefinition, LookAtPlayerGoalDefinition {

  /** Returns the selector priority, where lower values run first. */
  int priority();
}
