package dev.vexsoft.core.paper.service.packets.v26_2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.packets.display.DisplayGlowColor;
import dev.vexsoft.core.paper.packets.service.PacketTransportAdapterService;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.server.Bootstrap;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Verifies cleanup after viewers leave a mob's world without sending old entity metadata. */
public final class VexEntityEffectCleanupTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void removingUnglowedPetAcrossWorldsIsHarmless() {
        var fixture = new Fixture();
        fixture.service.clearGlow(fixture.viewer, fixture.target);
        assertTrue(fixture.sent.isEmpty());
        assertThrows(IllegalArgumentException.class,
            () -> fixture.service.setGlow(fixture.viewer, fixture.target, DisplayGlowColor.of(0xFFFFFF)));
    }

    @Test
    void worldChangeRemovesExistingTeamOnceWithoutEntityMetadata() throws Exception {
        var fixture = new Fixture();
        var field = VexEntityEffectPacketAdapterService.class.getDeclaredField("glowTeams");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Set<String> teams = (Set<String>) field.get(fixture.service);
        teams.add(fixture.viewerId + ":42");

        fixture.service.clearGlow(fixture.viewer, fixture.target);
        fixture.service.clearGlow(fixture.viewer, fixture.target);

        assertTrue(teams.isEmpty());
        assertEquals(1, fixture.sent.size());
        var packet = (ClientboundSetPlayerTeamPacket) fixture.sent.getFirst();
        assertEquals("vx16", packet.getName());
        assertEquals(ClientboundSetPlayerTeamPacket.Action.REMOVE, packet.getTeamAction());
    }

    private static final class Fixture {

        private final UUID viewerId = UUID.randomUUID();
        private final List<Object> sent = new ArrayList<>();
        private final World viewerWorld = world();
        private final World targetWorld = world();
        private final Player viewer = proxy(Player.class, (instance, method, arguments) -> switch (method.getName()) {
            case "getUniqueId" -> viewerId;
            case "getWorld" -> viewerWorld;
            default -> throw new AssertionError("Unexpected viewer call: " + method.getName());
        });
        private final LivingEntity target = proxy(LivingEntity.class,
            (instance, method, arguments) -> switch (method.getName()) {
                case "getEntityId" -> 42;
                case "getWorld" -> targetWorld;
                default -> throw new AssertionError("Unexpected target call: " + method.getName());
            });
        private final VexEntityEffectPacketAdapterService service;

        private Fixture() {
            var transport = proxy(PacketTransportAdapterService.class, (instance, method, arguments) -> {
                assertEquals("send", method.getName());
                sent.add(arguments[1]);
                return null;
            });
            service = new VexEntityEffectPacketAdapterService(proxy(VexServiceRegistry.class,
                (instance, method, arguments) -> transport));
        }

        private static World world() {
            return proxy(World.class, (instance, method, arguments) -> {
                assertEquals("equals", method.getName());
                return instance == arguments[0];
            });
        }
    }

    private static <T> T proxy(final Class<T> type, final InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }
}
