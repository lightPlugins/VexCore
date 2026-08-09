package dev.vexsoft.core.gameplay.stat;

/** Removable ownership handle for one applied runtime modifier. */
public interface StatModifierHandle extends AutoCloseable {

  /** Returns whether the modifier is still attached to its original stat registration. */
  boolean isActive();

  /** Removes the modifier; repeated calls have no effect. */
  void remove();

  @Override
  default void close() {
    remove();
  }
}
