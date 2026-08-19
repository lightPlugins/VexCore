package dev.vexsoft.core.paper.packets.display;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

/** Verifies damage-stage validation and defensive request values. */
final class FakeBlockDamageOverlayRequestTest {

  @Test
  void acceptsHiddenAndVisibleDamageStages() {
    FakeBlockDamageOverlayRequest hidden = FakeBlockDamageOverlayRequest.builder(
        location(),
        new NamespacedKey("test", "damage/crack")
    ).build();
    FakeBlockDamageOverlayRequest cracked = FakeBlockDamageOverlayRequest.builder(
        location(),
        new NamespacedKey("test", "damage/crack")
    ).damageStage(9).build();

    assertEquals(-1, hidden.getDamageStage());
    assertEquals(9, cracked.getDamageStage());
  }

  @Test
  void rejectsDamageStagesOutsideTheProtocolRange() {
    assertThrows(
        IllegalArgumentException.class,
        () -> FakeBlockDamageOverlayRequest.builder(
            location(),
            new NamespacedKey("test", "damage/crack")
        ).damageStage(10).build()
    );
    assertThrows(
        IllegalArgumentException.class,
        () -> FakeBlockDamageOverlayUpdate.stage(-2)
    );
  }

  private static Location location() {
    World world = (World) Proxy.newProxyInstance(
        World.class.getClassLoader(),
        new Class<?>[]{World.class},
        (proxy, method, arguments) -> defaultValue(method.getReturnType())
    );
    return new Location(world, 1.0D, 2.0D, 3.0D);
  }

  private static Object defaultValue(final Class<?> type) {
    if (!type.isPrimitive()) {
      return null;
    }
    if (type == boolean.class) {
      return false;
    }
    if (type == char.class) {
      return '\0';
    }
    return 0;
  }
}
