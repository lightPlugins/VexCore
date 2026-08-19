package dev.vexsoft.core.paper.service.packets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.packets.service.BlockDamageOverlayPacketService;
import dev.vexsoft.core.paper.packets.service.ItemDisplayPacketService;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

/** Verifies the owner-scoped damage-overlay service contract. */
final class VexBlockDamageOverlayPacketServiceTest {

  @Test
  void declaresItemDisplayDependencyAndRegistryConstructor() throws NoSuchMethodException {
    Dependencies dependencies = VexBlockDamageOverlayPacketService.class
        .getAnnotation(Dependencies.class);
    Constructor<VexBlockDamageOverlayPacketService> constructor =
        VexBlockDamageOverlayPacketService.class.getConstructor(VexServiceRegistry.class);

    assertArrayEquals(new Class<?>[]{ItemDisplayPacketService.class}, dependencies.value());
    assertTrue(Modifier.isPublic(constructor.getModifiers()));
    assertTrue(BlockDamageOverlayPacketService.class.isAssignableFrom(
        VexBlockDamageOverlayPacketService.class
    ));
    assertTrue(AutoCloseable.class.isAssignableFrom(VexBlockDamageOverlayPacketService.class));
  }
}
