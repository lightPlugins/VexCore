package dev.vexsoft.core.action;

/** Returns false when a required action must be retried. Persistent actions must be idempotent. */
@FunctionalInterface
public interface CompiledAction<C> {

    /** Executes the action and returns false when it must be retried. */
    boolean execute(C context);
}
