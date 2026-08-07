package dev.vexsoft.core.api.player;

/**
 * Marks a feature facade bound to one loaded {@link VexPlayer} session.
 *
 * <p>Containers expose player-specific behavior without requiring callers to pass the player to
 * every operation. Implementations may release session resources when closed.</p>
 */
public interface PlayerContainer extends AutoCloseable {

  /** Reacts after one persistent data value was replaced with a fresh default. */
  default void onDataReset(final DataContainerKey<?> key) {
  }

  /** Releases resources owned by this player's container. */
  @Override
  default void close() {
  }
}
