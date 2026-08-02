package dev.vexsoft.core.api.player;

/**
 * Collects the typed player data containers provided by a plugin
 */
public interface DataContainerRegistry {

  /** Registers a container owned by the current plugin */
  public void register(DataContainerKey<?> key);
}
