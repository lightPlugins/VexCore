package dev.vexsoft.core.api.player;

public interface DataContainerRegistry {

  /** Registers a container owned by the current plugin */
  public void register(DataContainerKey<?> key);
}
