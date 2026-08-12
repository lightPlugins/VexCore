package dev.vexsoft.core.level;

import dev.vexsoft.core.api.player.DataContainerKey;
import dev.vexsoft.core.api.player.VexPlayer;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Binds a plugin-owned player data container to the generic level runtime.
 *
 * <p>All reads and claimed-level mutations are routed through {@link VexPlayer}; the backing data
 * object is never mutated outside {@code VexPlayer.update(...)}.</p>
 */
public final class LevelProgressAccess<T> {

  private final DataContainerKey<T> key;
  private final Function<T, LevelProgress> reader;
  private final BiConsumer<T, Integer> claimedLevelWriter;

  /** Creates one reusable binding for a progression type such as a skill or collection. */
  public LevelProgressAccess(
      final DataContainerKey<T> key,
      final Function<T, LevelProgress> reader,
      final BiConsumer<T, Integer> claimedLevelWriter
  ) {
    this.key = Objects.requireNonNull(key, "key");
    this.reader = Objects.requireNonNull(reader, "reader");
    this.claimedLevelWriter = Objects.requireNonNull(
        claimedLevelWriter, "claimedLevelWriter"
    );
  }

  /** Reads a stable immutable progress view through the player container API. */
  public LevelProgress read(final VexPlayer player) {
    return Objects.requireNonNull(player, "player").read(
        key,
        data -> Objects.requireNonNull(reader.apply(data), "level progress")
    );
  }

  /** Updates the claimed level exclusively through the player container API. */
  public void updateClaimedLevel(final VexPlayer player, final int level) {
    Objects.requireNonNull(player, "player").update(
        key,
        (Consumer<T>) data -> claimedLevelWriter.accept(data, level)
    );
  }
}
