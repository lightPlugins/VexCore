package dev.vexsoft.core.paper.signals.core;

import dev.vexsoft.core.paper.signals.SignalAttributes;
import dev.vexsoft.core.paper.signals.VexSignal;
import dev.vexsoft.core.api.player.VexPlayer;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.Value;
import net.kyori.adventure.key.Key;

/**
 * Signals that a joined player's shared data is fully loaded and available.
 *
 * <p>VexCore publishes this signal once during a successful join, on the Paper server thread and
 * after the player has been placed in the shared player cache. Its amount is always {@code 1}.</p>
 */
@Value
public class PlayerDataLoadedSignal implements VexSignal {

  /** Stable key used for key-based subscriptions to this signal. */
  public static final Key KEY = Key.key("vexcore", "player_data_loaded");

  VexPlayer player;
  SignalAttributes attributes;

  /**
   * Creates a player-data-loaded signal.
   *
   * @param player fully loaded player session
   */
  public PlayerDataLoadedSignal(final VexPlayer player) {
    this.player = Objects.requireNonNull(player, "player");
    attributes = SignalAttributes.builder().putString("player_name", player.getName()).build();
  }

  @Override
  public Key getKey() {
    return KEY;
  }

  @Override
  public Optional<UUID> getSubject() {
    return Optional.of(player.getUniqueId());
  }

  @Override
  public long getAmount() {
    return 1L;
  }

  @Override
  public SignalAttributes getAttributes() {
    return attributes;
  }

}
