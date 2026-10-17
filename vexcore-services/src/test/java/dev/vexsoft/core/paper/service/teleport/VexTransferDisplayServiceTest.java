package dev.vexsoft.core.paper.service.teleport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.api.teleport.TransferOptions;
import dev.vexsoft.core.api.teleport.TransferPresentation;
import dev.vexsoft.core.paper.service.scheduler.ScheduleService;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

/** Verifies destination audio follows successful progress removal and remains fenced by the arrival request. */
final class VexTransferDisplayServiceTest {

    @Test
    void playsOnceAtTheDestinationOnlyAfterSuccessfulProgressCompletion() {
        try (var fixture = new Fixture()) {
            UUID request = UUID.randomUUID();
            fixture.service.show(fixture.owner, request, presentation(0.8F));
            assertEquals(List.of("show"), fixture.events);
            fixture.location = new Location(null, 100.5, 72, -340.5);
            fixture.service.finish(fixture.owner, request, true);
            assertEquals(List.of("show", "hide", "sound"), fixture.events);
            assertEquals(List.of(fixture.location), fixture.sounds);
            fixture.service.finish(fixture.owner, request, true);
            assertEquals(1, fixture.sounds.size());
        }
    }

    @Test
    void failedStaleDisconnectedAndMutedArrivalsRemainSilent() {
        try (var fixture = new Fixture()) {
            UUID failed = UUID.randomUUID();
            UUID current = UUID.randomUUID();
            fixture.service.show(fixture.owner, failed, presentation(0.8F));
            fixture.service.finish(fixture.owner, failed, false);
            assertEquals(0, fixture.sounds.size());
            fixture.service.show(fixture.owner, current, presentation(0));
            fixture.service.finish(fixture.owner, failed, true);
            fixture.delayed.forEach(Runnable::run);
            fixture.service.finish(fixture.owner, current, true);
            assertEquals(0, fixture.sounds.size());
            fixture.service.show(fixture.owner, current, presentation(0.8F));
            fixture.service.disconnected(fixture.owner);
            fixture.service.finish(fixture.owner, current, true);
            assertEquals(0, fixture.sounds.size());
            fixture.service.show(fixture.owner, current, presentation(0.8F));
            fixture.connected = false;
            fixture.service.finish(fixture.owner, current, true);
            assertEquals(0, fixture.sounds.size());
        }
    }

    @Test
    void transferOptionsRoundTripWithSoundAndOlderPresentationsStaySilent() throws Exception {
        var mapper = new ObjectMapper();
        var options = new TransferOptions(null, presentation(0.8F), false);
        assertEquals(options, mapper.readValue(mapper.writeValueAsBytes(options), TransferOptions.class));
        var old = new TransferPresentation("{\"text\":\"Loading\"}", "{\"text\":\"Failed\"}", "PURPLE");
        var serialized = mapper.valueToTree(old);
        ((com.fasterxml.jackson.databind.node.ObjectNode) serialized).remove("arrivalSound");
        var decoded = mapper.treeToValue(serialized, TransferPresentation.class);
        assertNull(decoded.arrivalSound());
        try (var fixture = new Fixture()) {
            UUID request = UUID.randomUUID();
            fixture.service.show(fixture.owner, request, decoded);
            fixture.service.finish(fixture.owner, request, true);
            assertEquals(List.of("show", "hide"), fixture.events);
        }
        assertThrows(IllegalArgumentException.class,
            () -> new TransferPresentation.Sound("invalid", 1, 1));
        assertThrows(IllegalArgumentException.class,
            () -> new TransferPresentation.Sound("minecraft:entity.enderman.teleport", Float.NaN, 1));
    }

    private static TransferPresentation presentation(final float volume) {
        return new TransferPresentation("{\"text\":\"Loading\"}", "{\"text\":\"Failed\"}", "PURPLE",
            new TransferPresentation.Sound("minecraft:entity.enderman.teleport", volume, 1));
    }

    private static <T> T proxy(final Class<T> type, final InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    private static final class Fixture implements AutoCloseable {

        private final VexPlayer owner = new VexPlayer(UUID.randomUUID(), "Alex", type -> -1);
        private final List<String> events = new ArrayList<>();
        private final List<Location> sounds = new ArrayList<>();
        private final List<Runnable> delayed = new ArrayList<>();
        private Location location = new Location(null, 0, 64, 0);
        private boolean connected = true;
        private final Player player = proxy(Player.class, (object, method, arguments) -> switch (method.getName()) {
            case "isConnected" -> connected;
            case "getLocation" -> location.clone();
            case "showBossBar" -> {
                events.add("show");
                yield null;
            }
            case "hideBossBar" -> {
                events.add("hide");
                yield null;
            }
            case "playSound" -> {
                assertEquals("minecraft:entity.enderman.teleport", arguments[1]);
                events.add("sound");
                sounds.add(((Location) arguments[0]).clone());
                yield null;
            }
            default -> throw new AssertionError(method.getName());
        });
        private final ScheduleService schedules = proxy(ScheduleService.class, (object, method, arguments) -> {
            if (method.getName().equals("runFor")) {
                ((Runnable) arguments[1]).run();
            } else if (method.getName().equals("runForLater")) {
                delayed.add((Runnable) arguments[2]);
            } else {
                throw new AssertionError(method.getName());
            }
            return Optional.empty();
        });
        private final VexTransferDisplayService service = new VexTransferDisplayService(
            proxy(VexServiceRegistry.class, (object, method, arguments) -> schedules));

        private Fixture() {
            owner.bindPlatformPlayer(player);
        }

        @Override
        public void close() {
            service.close();
        }
    }
}
