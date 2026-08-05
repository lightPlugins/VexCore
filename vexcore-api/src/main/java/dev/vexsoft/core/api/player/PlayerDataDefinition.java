package dev.vexsoft.core.api.player;

/**
 * Declares the cached and persisted player-data containers supplied by a plugin.
 *
 * <p>Implementations are instantiated during plugin loading and should only register stable keys;
 * they must not perform Bukkit runtime work in this callback.</p>
 */
public interface PlayerDataDefinition {

  /**
   * Registers every player-data container provided by this definition.
   *
   * @param registry collector bound to the owning plugin
   */
  void register(DataContainerRegistry registry);
}
