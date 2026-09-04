package dev.vexsoft.core.paper.mob.spawner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.paper.mob.MobKey;
import dev.vexsoft.core.paper.mob.MobScope;
import dev.vexsoft.core.paper.mob.MobSpawnRequest;
import java.lang.reflect.Proxy;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

class MobSpawnerFoundationTest {

  @Test
  void validatesActivationHysteresis() {
    Location anchor = new Location(world(), 10.5D, 64.0D, -3.5D);

    assertThrows(IllegalArgumentException.class, () -> MobSpawnerDefinition.builder(
        MobSpawnerKey.of("arcane_monolith", "invalid"),
        MobKey.of("arcane_monolith", "chicken"),
        anchor
    ).activationRadii(40.0D, 32.0D).build());
  }

  @Test
  void keepsSpawnAndMovementOriginsIndependentAndDefensive() {
    World world = world();
    Location spawn = new Location(world, 15.0D, 65.0D, 15.0D);
    Location anchor = new Location(world, 10.0D, 64.0D, 10.0D);
    MobSpawnRequest request = new MobSpawnRequest(
        MobKey.of("arcane_monolith", "chicken"), spawn, MobScope.global(), anchor
    );

    assertEquals(spawn, request.location());
    assertEquals(anchor, request.movementOrigin());
    assertNotSame(spawn, request.location());
    assertNotSame(anchor, request.movementOrigin());
  }

  @Test
  void appliesConservativeSpawnerDefaults() {
    MobSpawnerDefinition definition = MobSpawnerDefinition.builder(
        MobSpawnerKey.of("arcane_monolith", "cave"),
        MobKey.of("arcane_monolith", "chicken"),
        new Location(world(), 0.0D, 40.0D, 0.0D)
    ).scope(MobSpawnerScope.PER_PLAYER).build();

    assertEquals(4, definition.maximumAlive());
    assertEquals(1, definition.spawnBatchSize());
    assertEquals(32.0D, definition.activationRadius());
    assertEquals(40.0D, definition.deactivationRadius());
    assertEquals(3, definition.positionRules().clearanceBlocks());
  }

  @Test
  void supportsAnExactAnchorPositionForPlacedNpcs() {
    Location anchor = new Location(world(), 1.25D, 64.75D, -3.125D, 137.0F, -12.0F);
    MobSpawnerDefinition definition = MobSpawnerDefinition.builder(
        MobSpawnerKey.of("arcane_monolith", "blacksmith"),
        MobKey.of("arcane_monolith", "blacksmith"),
        anchor
    ).exactSpawnPosition(true).build();

    assertTrue(definition.exactSpawnPosition());
    assertEquals(anchor, definition.anchor());
  }

  private static World world() {
    return (World) Proxy.newProxyInstance(
        World.class.getClassLoader(),
        new Class<?>[]{World.class},
        (proxy, method, arguments) -> switch (method.getName()) {
          case "equals" -> proxy == arguments[0];
          case "hashCode" -> System.identityHashCode(proxy);
          case "toString" -> "test-world";
          default -> defaultValue(method.getReturnType());
        }
    );
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
