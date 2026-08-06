package dev.vexsoft.core.api.signal.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.UUID;
import dev.vexsoft.core.api.player.VexPlayer;
import org.junit.jupiter.api.Test;

class PlayerDataLoadedSignalTest {

  @Test
  void exposesStablePlayerDataAndQuantity() {
    UUID playerId = UUID.randomUUID();
    VexPlayer player = new VexPlayer(playerId, "Philipp");
    PlayerDataLoadedSignal signal = new PlayerDataLoadedSignal(player);

    assertEquals(PlayerDataLoadedSignal.KEY, signal.getKey());
    assertSame(player, signal.getPlayer());
    assertEquals(playerId, signal.getSubject().orElseThrow());
    assertEquals("Philipp", signal.getAttributes().findString("player_name").orElseThrow());
    assertEquals(1L, signal.getAmount());
    assertSame(signal.getAttributes(), signal.getAttributes());
  }
}
