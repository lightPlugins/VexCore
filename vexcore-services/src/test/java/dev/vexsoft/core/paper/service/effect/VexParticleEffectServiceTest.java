package dev.vexsoft.core.paper.service.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.common.service.registry.DefaultServiceRegistry;
import dev.vexsoft.core.paper.effect.RingBurst;
import dev.vexsoft.core.paper.scheduler.VexTask;
import dev.vexsoft.core.paper.service.scheduler.ScheduleService;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

/** Uses the real scoped registry/factory and verifies geometry, retirement, replacement, and shutdown. */
final class VexParticleEffectServiceTest {

    private static final RingBurst RING = new RingBurst(2, 0.05, 6, 48, 0x8B5CF6, 0.9F);

    @Test
    void registeredServiceExpandsAtFootHeightAndStopsAfterItsFiniteFrames() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.service.ringBurst(fixture.player, RING);
            assertEquals(1, fixture.timers.size());
            var timer = fixture.timers.getFirst();
            for (int frame = 0; frame < RING.frames(); frame++) {
                timer.callback.run();
            }
            assertTrue(timer.cancelled);
            assertEquals(RING.frames() * RING.points(), fixture.particles.size());
            for (Object[] emission : fixture.particles) {
                assertEquals(64.05, (double) emission[2], 0.00001);
                assertEquals(0x8B5CF6, ((Particle.DustOptions) emission[9]).getColor().asRGB());
            }
            Object[] lastFrame = fixture.particles.get((RING.frames() - 1) * RING.points());
            assertEquals(12D, (double) lastFrame[1], 0.00001);
            assertEquals(20D, (double) lastFrame[3], 0.00001);
        }
    }

    @Test
    void largeWaveFadesOverFourSecondsWithRisingFountainsAndBoundedEmissions() throws Exception {
        var wave = new RingBurst(10, 0.05, 40, 96, 0x8B5CF6, 0.9F, 2.5, 16);
        try (var fixture = new Fixture()) {
            fixture.service.ringBurst(fixture.player, wave);
            var timer = fixture.timers.getFirst();
            double previousRadius = 0;
            float previousSize = Float.POSITIVE_INFINITY;
            double maximumHeight = 0;
            for (int frame = 0; frame < wave.frames(); frame++) {
                int start = fixture.particles.size();
                timer.callback.run();
                var emissions = fixture.particles.subList(start, fixture.particles.size());
                Object[] edge = emissions.getFirst();
                double radius = Math.hypot((double) edge[1] - 10, (double) edge[3] - 20);
                float size = ((Particle.DustOptions) edge[9]).getSize();
                assertTrue(radius > previousRadius);
                assertTrue(size < previousSize);
                assertEquals(frame == wave.frames() - 1, timer.cancelled);
                for (Object[] emission : emissions) {
                    double height = (double) emission[2] - 64.05;
                    double distance = Math.hypot((double) emission[1] - 10, (double) emission[3] - 20);
                    assertTrue(height >= 0 && height <= wave.fountainHeight() + 0.00001);
                    assertTrue(distance <= radius + wave.fountainHeight() * 0.15 + 0.00001);
                    assertEquals(wave.color(), ((Particle.DustOptions) emission[9]).getColor().asRGB());
                    if (height == 0) {
                        assertTrue(distance <= radius + 0.00001);
                    }
                    maximumHeight = Math.max(maximumHeight, height);
                }
                previousRadius = radius;
                previousSize = size;
            }
            assertEquals(20, previousRadius * 2, 0.00001);
            assertEquals(wave.fountainHeight(), maximumHeight, 0.00001);
            assertTrue(fixture.particles.size() <= wave.frames()
                * (wave.points() + (wave.points() + 3) / 4 + wave.fountains() * 2));
            int total = fixture.particles.size();
            timer.callback.run();
            assertEquals(total, fixture.particles.size());
        }
    }

    @Test
    void replacementDisconnectRetirementAndShutdownCancelOwnedAnimations() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.service.ringBurst(fixture.player, RING);
            fixture.service.ringBurst(fixture.player, RING);
            assertTrue(fixture.timers.getFirst().cancelled);
            fixture.timers.getFirst().callback.run();
            assertTrue(fixture.particles.isEmpty());
            fixture.online = false;
            fixture.timers.getLast().callback.run();
            assertTrue(fixture.timers.getLast().cancelled);
            assertTrue(fixture.particles.isEmpty());
            fixture.online = true;
            fixture.service.ringBurst(fixture.player, RING);
            fixture.timers.getLast().retired.run();
            assertTrue(fixture.timers.getLast().cancelled);
            fixture.service.ringBurst(fixture.player, RING);
            fixture.services.unregisterOwnedServices();
            assertTrue(fixture.timers.getLast().cancelled);
        }
    }

    @Test
    void invalidOrUnboundedParticleSettingsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new RingBurst(Double.NaN, 0.05, 6, 48, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new RingBurst(2, 0.05, 999, 48, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new RingBurst(2, 0.05, 6, 9999, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new RingBurst(10, 0.05, 40, 96, 0, 1, Double.NaN, 16));
        assertThrows(IllegalArgumentException.class, () -> new RingBurst(10, 0.05, 40, 96, 0, 1, 2.5, 999));
        assertThrows(IllegalArgumentException.class, () -> new RingBurst(10, 0.05, 40, 96, 0, 1, 0, 16));
        assertThrows(IllegalArgumentException.class, () -> new RingBurst(10, 0.05, 40, 96, 0, 1, 2.5, 0));
    }

    private static final class Fixture implements AutoCloseable {

        private final DefaultServiceRegistry registry = new DefaultServiceRegistry();
        private final ServiceOwner owner = () -> "ParticleTest";
        private final dev.vexsoft.core.api.service.registry.VexServiceRegistry services = registry.scoped(owner);
        private final List<Timer> timers = new ArrayList<>();
        private final List<Object[]> particles = new ArrayList<>();
        private final World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(),
            new Class<?>[]{World.class}, (object, method, arguments) -> null);
        private final UUID id = UUID.randomUUID();
        private boolean online = true;
        private final Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
            new Class<?>[]{Player.class}, (object, method, arguments) -> switch (method.getName()) {
                case "getUniqueId" -> id;
                case "isOnline" -> online;
                case "getWorld" -> world;
                case "getLocation" -> new Location(world, 10, 64, 20);
                case "spawnParticle" -> {
                    particles.add(arguments.clone());
                    yield null;
                }
                default -> null;
            });
        private final ParticleEffectService service;

        private Fixture() throws Exception {
            var schedules = (ScheduleService) Proxy.newProxyInstance(ScheduleService.class.getClassLoader(),
                new Class<?>[]{ScheduleService.class}, (object, method, arguments) -> {
                    if (method.getName().equals("runFor")) {
                        ((Runnable) arguments[1]).run();
                        return Optional.empty();
                    }
                    assertEquals("runForTimer", method.getName());
                    assertEquals(2L, arguments[2]);
                    var timer = new Timer((Runnable) arguments[3], (Runnable) arguments[4]);
                    timers.add(timer);
                    return Optional.of(timer);
                });
            var install = DefaultServiceRegistry.class.getDeclaredMethod("register", ServiceOwner.class,
                Class.class, VexService.class);
            install.setAccessible(true);
            install.invoke(registry, owner, ScheduleService.class, schedules);
            services.register(ParticleEffectService.class, VexParticleEffectService.class);
            services.registerQueuedServices();
            service = services.require(ParticleEffectService.class);
        }

        @Override
        public void close() {
            services.unregisterOwnedServices();
        }
    }

    private static final class Timer implements VexTask {

        private final Runnable callback;
        private final Runnable retired;
        private boolean cancelled;

        private Timer(final Runnable callback, final Runnable retired) {
            this.callback = callback;
            this.retired = retired;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public boolean isFinished() {
            return cancelled;
        }

        @Override
        public boolean isRepeating() {
            return true;
        }
    }
}
