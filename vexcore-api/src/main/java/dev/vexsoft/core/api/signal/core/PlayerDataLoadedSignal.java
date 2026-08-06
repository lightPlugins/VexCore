package dev.vexsoft.core.api.signal.core;

import dev.vexsoft.core.api.signal.SignalAttributes;
import dev.vexsoft.core.api.signal.VexSignal;
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

  UUID playerId;
  String playerName;
  SignalAttributes attributes;

  /**
   * Creates a player-data-loaded signal.
   *
   * @param playerId unique identifier of the loaded player
   * @param playerName current name of the loaded player
   */
  public PlayerDataLoadedSignal(final UUID playerId, final String playerName) {
    this.playerId = Objects.requireNonNull(playerId, "playerId");
    this.playerName = requireName(playerName);
    attributes = SignalAttributes.builder().putString("player_name", this.playerName).build();
  }

  @Override
  public Key getKey() {
    return KEY;
  }

  @Override
  public Optional<UUID> getSubject() {
    return Optional.of(playerId);
  }

  @Override
  public long getAmount() {
    return 1L;
  }

  @Override
  public SignalAttributes getAttributes() {
    return attributes;
  }

  private static String requireName(final String playerName) {
    String checkedName = Objects.requireNonNull(playerName, "playerName").trim();
    if (checkedName.isEmpty()) {
      throw new IllegalArgumentException("playerName must not be empty");
    }
    return checkedName;
  }
}
