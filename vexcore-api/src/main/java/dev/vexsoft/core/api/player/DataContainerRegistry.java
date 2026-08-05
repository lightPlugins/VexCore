package dev.vexsoft.core.api.player;

/** Collects typed player-data container keys during definition registration. */
public interface DataContainerRegistry {

  /**
   * Registers a container owned by the current plugin.
   *
   * @param key stable, SQL-safe key and default-value supplier
   * @throws IllegalStateException if the owner already registered another key with the same name
   */
  void register(DataContainerKey<?> key);
}
