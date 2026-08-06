package dev.vexsoft.core.api.signal.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlayerDataLoadedSignalTest {

  @Test
  void exposesStablePlayerDataAndQuantity() {
    UUID playerId = UUID.randomUUID();
    PlayerDataLoadedSignal signal = new PlayerDataLoadedSignal(playerId, "Philipp");

    assertEquals(PlayerDataLoadedSignal.KEY, signal.getKey());
    assertEquals(playerId, signal.getPlayerId());
    assertEquals(playerId, signal.getSubject().orElseThrow());
    assertEquals("Philipp", signal.getPlayerName());
    assertEquals("Philipp", signal.getAttributes().findString("player_name").orElseThrow());
    assertEquals(1L, signal.getAmount());
    assertSame(signal.getAttributes(), signal.getAttributes());
  }
}
