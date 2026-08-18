package dev.vexsoft.core.paper.packets.interaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.vexsoft.core.paper.packets.display.DisplayLifecycle;
import java.lang.reflect.Proxy;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

/** Verifies validation and defensive copies for virtual interaction requests. */
final class FakeInteractionRequestTest {

  @Test
  void validatesDimensionsAndCopiesMutableInputs() {
    Location source = location(1.0D, 2.0D, 3.0D);
    FakeInteractionRequest request = FakeInteractionRequest.builder(source)
        .width(2.5F)
        .height(3.5F)
        .lifecycle(Set.of(DisplayLifecycle.PLAYER_DEATH))
        .build();

    source.setX(99.0D);
    Location returned = request.getLocation();
    returned.setY(99.0D);

    assertEquals(1.0D, request.getLocation().getX());
    assertEquals(2.0D, request.getLocation().getY());
    assertEquals(2.5F, request.getWidth());
    assertEquals(Set.of(DisplayLifecycle.PLAYER_DEATH), request.getLifecycle());
  }

  @Test
  void rejectsNonPositiveOrNonFiniteDimensions() {
    Location location = location(0.0D, 0.0D, 0.0D);

    assertThrows(
        IllegalArgumentException.class,
        () -> FakeInteractionRequest.builder(location).width(0.0F).build()
    );
    assertThrows(
        IllegalArgumentException.class,
        () -> FakeInteractionRequest.builder(location).height(Float.NaN).build()
    );
  }

  private static Location location(final double x, final double y, final double z) {
    World world = (World) Proxy.newProxyInstance(
        World.class.getClassLoader(),
        new Class<?>[]{World.class},
        (proxy, method, arguments) -> defaultValue(method.getReturnType())
    );
    return new Location(world, x, y, z);
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
