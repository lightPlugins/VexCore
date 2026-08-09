package dev.vexsoft.core.paper.signals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;

class SignalAttributesTest {

  @Test
  void storesEverySupportedType() {
    UUID playerId = UUID.randomUUID();
    Key itemKey = Key.key("vexitems", "ruby");
    SignalAttributes attributes = SignalAttributes.builder()
        .putString("name", "Ruby")
        .putLong("level", 3L)
        .putDouble("progress", 0.5D)
        .putBoolean("rare", true)
        .putUuid("player", playerId)
        .putKey("item", itemKey)
        .build();

    assertEquals("Ruby", attributes.findString("NAME").orElseThrow());
    assertEquals(3L, attributes.findLong("level").orElseThrow());
    assertEquals(0.5D, attributes.findDouble("progress").orElseThrow());
    assertEquals(true, attributes.findBoolean("rare").orElseThrow());
    assertEquals(playerId, attributes.findUuid("player").orElseThrow());
    assertEquals(itemKey, attributes.findKey("item").orElseThrow());
  }

  @Test
  void reusesEmptyInstanceAndRejectsInvalidValues() {
    assertSame(SignalAttributes.empty(), SignalAttributes.builder().build());
    assertThrows(
        IllegalArgumentException.class,
        () -> SignalAttributes.builder().putString("same", "first").putLong("same", 2L)
    );
    assertThrows(
        IllegalArgumentException.class,
        () -> SignalAttributes.builder().putDouble("value", Double.NaN)
    );
  }
}
