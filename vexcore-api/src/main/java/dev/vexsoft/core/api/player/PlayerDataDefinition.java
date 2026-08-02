package dev.vexsoft.core.api.player;

public interface PlayerDataDefinition {

  /** Registers every player data container provided by this definition */
  public void register(DataContainerRegistry registry);
}
