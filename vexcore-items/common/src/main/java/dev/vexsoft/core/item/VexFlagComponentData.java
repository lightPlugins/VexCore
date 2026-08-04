package dev.vexsoft.core.item;

/**
 * Identifies a component that carries no additional value
 */
public final class VexFlagComponentData extends VexComponentData<Void> {

  VexFlagComponentData(final VexComponentKey key, final VexComponentTarget target) {
    super(key, target, Void.class, value -> value);
  }
}
