package dev.vexsoft.core.action;

/** Returns false when a required action must be retried. Persistent actions must be idempotent. */
@FunctionalInterface
public interface CompiledAction<C> {
  boolean execute(C context);
}
