package dev.vexsoft.core.api.player;

/**
 * Defines the cached player data containers supplied by a plugin
 */
public interface PlayerDataDefinition {

  /** Registers every player data container provided by this definition */
  public void register(DataContainerRegistry registry);
}
