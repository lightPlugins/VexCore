package dev.vexsoft.core.paper.service.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexClassFactory;
import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.common.service.registry.DefaultServiceRegistry;
import dev.vexsoft.core.paper.effect.ItemCollectionRequest;
import dev.vexsoft.core.paper.packets.display.FakeDisplayHandle;
import dev.vexsoft.core.paper.packets.display.FakeDisplayKind;
import dev.vexsoft.core.paper.packets.display.FakeItemDisplayRequest;
import dev.vexsoft.core.paper.packets.display.FakeItemDisplayUpdate;
import dev.vexsoft.core.paper.packets.service.ItemDisplayPacketService;
import dev.vexsoft.core.paper.scheduler.VexTask;
import dev.vexsoft.core.paper.service.scheduler.ScheduleService;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;

/** Exercises production registry construction, homing, collision ownership, burst budgets and late callbacks. */
public final class VexItemCollectionEffectServiceTest {

    @Test
    void followsAWalkingViewerWithInterpolatedSpinningDisplaysAndOneArrivalCue() throws Exception {
        try (var f = new Fixture()) {
            f.play();
            assertEquals(0, f.spawns);
            assertEquals(1, f.timers.size());
            f.drain();
            assertEquals(1, f.spawns);
            assertEquals(1, f.requests.getFirst().getTeleportDuration());
            assertEquals(1, f.requests.getFirst().getInterpolationDuration());
            assertEquals(0.38F, f.requests.getFirst().getTransformation().getScaleX());
            for (int tick = 0; tick < 120 && f.removals == 0; tick++) {
                f.position.add(0, 0, 0.28);
                f.tick(true);
            }
            assertEquals(1, f.removals);
            assertEquals(List.of("minecraft:block.amethyst_block.chime"), f.sounds);
            assertTrue(f.lastRemoved.distance(f.position.clone().add(0, 1, 0)) <= 0.7);
            assertTrue(f.timers.getFirst().cancelled);
            assertTrue(f.updates.size() >= 3);
            assertTrue(f.updates.getLast().getTransformation().getScaleX() < 0.38F);
            assertTrue(f.colors.contains(0x8B5CF6));
            assertTrue(f.colors.contains(0xDDD6FE));
            var a = f.updates.get(1).getTransformation();
            var b = f.updates.get(2).getTransformation();
            double dot = a.getLeftRotationX() * b.getLeftRotationX() + a.getLeftRotationY() * b.getLeftRotationY()
                + a.getLeftRotationZ() * b.getLeftRotationZ() + a.getLeftRotationW() * b.getLeftRotationW();
            assertTrue(Math.abs(dot) < 0.97, "The item must spin quickly between consecutive one-tick updates");
        }
    }

    @Test
    void locallyOwnedSpaceAvoidsRegionHandoffsAndKeepsOneTickInterpolation() throws Exception {
        try (var f = new Fixture()) {
            f.local = true;
            f.play();
            assertEquals(1, f.spawns);
            assertTrue(f.regionJobs.isEmpty());
            f.tick(true);
            assertEquals(1, f.updates.getFirst().getTeleportDuration());
            assertTrue(f.regionJobs.isEmpty());
        }
    }

    @Test
    void enclosedBlocksStillLaunchWhileUnavailableChunksNeverLoad() throws Exception {
        try (var f = new Fixture()) {
            f.caged = true;
            f.play();
            f.drain();
            assertEquals(1, f.spawns);
            assertTrue(f.sounds.isEmpty());
            assertTrue(f.blockReads <= 96 * 5);
            f.caged = false;
            f.loaded = false;
            f.blockReads = 0;
            f.play();
            f.drain();
            assertEquals(0, f.blockReads);
            f.loaded = true;
            f.owned = false;
            f.play();
            f.drain();
            assertEquals(0, f.blockReads);
            assertEquals(1, f.spawns);
            f.service.close();
            assertTrue(f.timers.stream().allMatch(timer -> timer.cancelled));
        }
    }

    @Test
    void personalBlockOverridesDetermineFreeSpace() throws Exception {
        try (var f = new Fixture()) {
            var solid = proxy(BlockData.class, (object, method, args) -> Material.STONE);
            f.service.play(f.player, new ItemCollectionRequest(f.origin(), new Stack(), f.valid::get,
                (x, y, z) -> solid));
            f.drain();
            assertEquals(1, f.spawns);
            assertTrue(f.requests.getFirst().getLocation().distance(f.origin()) < 0.2);
            f.caged = true;
            var air = proxy(BlockData.class, (object, method, args) -> Material.AIR);
            f.service.play(f.player, new ItemCollectionRequest(f.origin(), new Stack(), f.valid::get,
                (x, y, z) -> air));
            f.drain();
            assertEquals(2, f.spawns, "Both enclosed and personally open resources must launch");
            assertTrue(f.requests.getLast().getLocation().distance(f.origin()) > 0.7);
        }
    }

    @Test
    void pendingAndActiveFlightsShareGlobalCapacityAcrossConsumerScopes() throws Exception {
        try (var f = new Fixture()) {
            assertSame(f.service, f.services.scoped(() -> "isles").require(ItemCollectionEffectService.class));
            assertSame(f.service, f.services.scoped(() -> "other").require(ItemCollectionEffectService.class));
            var consumer = VexClassFactory.create(CollectionConsumer.class, f.services.scoped(() -> "consumer"),
                "Collection effect consumer");
            assertSame(f.service, consumer.service);
            for (int viewer = 0; viewer < 33; viewer++) {
                var player = f.viewer(UUID.randomUUID());
                for (int wave = 0; wave < 4; wave++) {
                    f.ticks += 2;
                    for (int count = 0; count < 4; count++) {
                        f.service.play(player, f.request());
                    }
                }
            }
            assertEquals(512, f.regionJobs.size(), "Extra requests wait without additional region work");
            assertTrue(f.sounds.isEmpty());
            f.drain();
            assertEquals(512, f.spawns);
            assertEquals(33, f.timers.size(), "There is one timer per viewer, never per item");
            f.service.close();
            assertEquals(512, f.removals);
            assertTrue(f.timers.stream().allMatch(timer -> timer.cancelled));
        }
    }

    @Test
    void largeBatchesKeepEveryBlockAndBoundVisibleDisplays() throws Exception {
        try (var f = new Fixture()) {
            for (int count = 0; count < 96; count++) {
                f.play();
            }
            assertEquals(16, f.regionJobs.size());
            assertTrue(f.service.hasCapacity(f.player));
            assertTrue(f.sounds.isEmpty());
            assertEquals(1, f.timers.size());
            f.drain();
            for (int tick = 0; tick < 200 && f.removals < 96; tick++) {
                f.tick(true);
                assertTrue(f.locations.size() <= 64);
            }
            assertEquals(96, f.spawns, "Every block in the batch must get its own display");
            assertEquals(96, f.removals);
            assertEquals(96, f.sounds.size());
            assertTrue(f.sounds.stream().allMatch(value -> value.contains("amethyst")));
            assertTrue(f.timers.getFirst().cancelled);
        }
    }

    @Test
    void staleProfilesAndLateRegionCallbacksCannotResurrectFlights() throws Exception {
        try (var f = new Fixture()) {
            f.play();
            f.valid.set(false);
            f.tick(false);
            f.drain();
            assertEquals(0, f.spawns);
            assertTrue(f.sounds.isEmpty());
            assertTrue(f.timers.getFirst().cancelled);
            f.valid.set(true);
            f.play();
            for (int tick = 0; tick < 121; tick++) {
                f.tick(false);
            }
            f.drain();
            assertEquals(0, f.spawns);
            assertTrue(f.sounds.isEmpty());
            assertTrue(f.timers.getLast().cancelled);
        }
    }

    @Test
    void distanceTeleportAndNewObstaclesRetireWithoutArrivalSound() throws Exception {
        try (var f = new Fixture()) {
            f.position.setX(50);
            f.play();
            assertTrue(f.regionJobs.isEmpty());
            f.position.setX(0.5);
            f.play();
            f.drain();
            f.tick(true);
            f.position.add(10, 0, 0);
            f.tick(true);
            assertEquals(1, f.removals);
            assertTrue(f.sounds.stream().noneMatch(value -> value.contains("amethyst")));
            f.position.setX(0.5);
            f.play();
            f.drain();
            f.caged = true;
            f.tick(true);
            assertEquals(1, f.removals, "New terrain must not silently cancel a cosmetic collection");
            for (int tick = 0; tick < 130 && f.removals < 2; tick++) {
                f.tick(true);
            }
            assertEquals(2, f.removals);
            assertEquals(List.of("minecraft:block.amethyst_block.chime"), f.sounds);
            assertTrue(f.timers.getLast().cancelled);
        }
    }

    @Test
    void deathLogoutAndWorldChangesRetireDisplaysAndTheirOnlyTimer() throws Exception {
        for (int scenario = 0; scenario < 3; scenario++) {
            try (var f = new Fixture()) {
                f.play();
                f.drain();
                if (scenario == 0) {
                    f.dead = true;
                } else if (scenario == 1) {
                    f.online = false;
                } else {
                    UUID otherWorld = UUID.randomUUID();
                    f.currentWorld = proxy(World.class, (object, method, args) -> otherWorld);
                    f.position.setWorld(f.currentWorld);
                }
                f.tick(true);
                assertEquals(1, f.removals);
                assertTrue(f.sounds.isEmpty());
                assertTrue(f.timers.getFirst().cancelled);
            }
        }
    }

    @Test
    void excessiveSeparationEndsWithinTheFiniteBudgetWithoutAnArrivalCue() throws Exception {
        try (var f = new Fixture()) {
            f.play();
            f.drain();
            for (int tick = 0; tick < 40 && f.removals == 0; tick++) {
                f.position.add(0, 0, 1.5);
                f.tick(true);
            }
            assertEquals(1, f.removals);
            assertTrue(f.sounds.isEmpty());
            assertTrue(f.timers.getFirst().cancelled);
        }
    }

    @Test
    void fourSimultaneousBlocksHaveDistinctLaunchesAndHoldForHalfASecond() throws Exception {
        try (var f = new Fixture()) {
            for (int block = 0; block < 4; block++) {
                f.play();
            }
            f.drain();
            assertEquals(4, f.spawns);
            assertEquals(4, f.requests.stream().map(value -> value.getLocation().toVector()).distinct().count());
            f.tick(true);
            f.tick(true);
            f.tick(true);
            f.tick(true);
            var apexes = new HashMap<>(f.locations);
            assertEquals(4, apexes.values().stream().map(Location::toVector).distinct().count());
            for (int hold = 0; hold < 10; hold++) {
                f.tick(true);
                assertEquals(apexes, f.locations, "Displays must stay at their own apex for ten ticks");
            }
            assertTrue(f.sounds.isEmpty(), "Reward pickup audio is independent; arrival has not happened yet");
            assertTrue(f.particleCount >= 64 && f.particleCount <= 168,
                "Fewer particles must remain visible across the same launch and hover duration");
            assertTrue(f.maxParticleCount <= 2, "Trails use at most two particles per cluster");
            f.tick(true);
            assertFalse(apexes.equals(f.locations));
            assertEquals(4, f.locations.values().stream().map(Location::toVector).distinct().count());
        }
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    private static final class Fixture implements AutoCloseable {
        private final DefaultServiceRegistry registry = new DefaultServiceRegistry();
        private final ServiceOwner owner = () -> "core";
        private final VexServiceRegistry services = registry.scoped(owner);
        private final Field serverField;
        private final Object previousServer;
        private final ArrayDeque<Runnable> regionJobs = new ArrayDeque<>();
        private final List<Timer> timers = new ArrayList<>();
        private final List<String> sounds = new ArrayList<>();
        private final List<Integer> colors = new ArrayList<>();
        private final List<FakeItemDisplayRequest> requests = new ArrayList<>();
        private final List<FakeItemDisplayUpdate> updates = new ArrayList<>();
        private final Map<FakeDisplayHandle, Location> locations = new HashMap<>();
        private final AtomicBoolean valid = new AtomicBoolean(true);
        private boolean inRegion;
        private boolean caged;
        private boolean loaded = true;
        private boolean owned = true;
        private boolean local;
        private boolean online = true;
        private boolean dead;
        private int ticks;
        private int blockReads;
        private int spawns;
        private int removals;
        private int particleCount;
        private int maxParticleCount;
        private Location lastRemoved;
        private final UUID worldId = UUID.randomUUID();
        private final World world = proxy(World.class, (object, method, args) -> switch (method.getName()) {
            case "getUID" -> worldId;
            case "equals" -> object == args[0];
            case "getMinHeight" -> -64;
            case "getMaxHeight" -> 320;
            case "isChunkLoaded" -> loaded;
            case "getBlockAt" -> {
                assertTrue(inRegion || local, "Live world reads require ownership of the block region");
                blockReads++;
                int x = (int) args[0];
                int y = (int) args[1];
                int z = (int) args[2];
                boolean solid = caged || y <= 63 || x == 4 && y == 64 && z == 0;
                yield proxy(Block.class, (block, call, values) -> switch (call.getName()) {
                    case "isPassable" -> !solid;
                    case "isLiquid" -> false;
                    case "getBoundingBox" -> new BoundingBox(x, y, z, x + 1, y + 1, z + 1);
                    default -> throw new AssertionError(call.getName());
                });
            }
            default -> throw new AssertionError(method.getName());
        });
        private final Location position = new Location(world, 0.5, 64, 0.5);
        private World currentWorld = world;
        private final Player player = viewer(UUID.randomUUID());
        private final VexItemCollectionEffectService service;

        private Fixture() throws Exception {
            serverField = Bukkit.class.getDeclaredField("server");
            serverField.setAccessible(true);
            previousServer = serverField.get(null);
            serverField.set(null, proxy(Server.class, (object, method, args) -> {
                assertEquals("isOwnedByCurrentRegion", method.getName());
                if (args.length == 1) {
                    return args[0] instanceof Player ? !inRegion : local;
                }
                return owned;
            }));
            install(ScheduleService.class, proxy(ScheduleService.class, (object, method, args) -> {
                switch (method.getName()) {
                    case "runForTimer":
                        assertEquals(1L, args[2]);
                        var timer = new Timer((Runnable) args[3]);
                        timers.add(timer);
                        return Optional.of(timer);
                    case "runAt":
                        regionJobs.add((Runnable) args[1]);
                        return new Timer(() -> { });
                    case "runFor":
                        boolean old = inRegion;
                        inRegion = false;
                        try {
                            ((Runnable) args[1]).run();
                        } finally {
                            inRegion = old;
                        }
                        return Optional.empty();
                    default:
                        throw new AssertionError(method.getName());
                }
            }));
            install(ItemDisplayPacketService.class, proxy(ItemDisplayPacketService.class, (object, method, args) -> {
                switch (method.getName()) {
                    case "spawn":
                        var request = (FakeItemDisplayRequest) args[1];
                        requests.add(request);
                        var handle = new FakeDisplayHandle(owner, ((Player) args[0]).getUniqueId(), ++spawns,
                            UUID.randomUUID(), FakeDisplayKind.ITEM);
                        locations.put(handle, request.getLocation());
                        return handle;
                    case "teleport":
                        locations.put((FakeDisplayHandle) args[0], ((Location) args[1]).clone());
                        return null;
                    case "update":
                        updates.add((FakeItemDisplayUpdate) args[1]);
                        return null;
                    case "remove":
                        lastRemoved = locations.remove(args[0]);
                        assertTrue(lastRemoved != null, "Each display is destroyed exactly once");
                        removals++;
                        return null;
                    default:
                        throw new AssertionError(method.getName());
                }
            }));
            services.register(ItemCollectionEffectService.class, VexItemCollectionEffectService.class);
            services.registerQueuedServices();
            service = (VexItemCollectionEffectService) services.require(ItemCollectionEffectService.class);
        }

        private Player viewer(UUID id) {
            return proxy(Player.class, (object, method, args) -> switch (method.getName()) {
                case "getUniqueId" -> id;
                case "isOnline" -> online;
                case "isDead" -> dead;
                case "getTicksLived" -> ticks;
                case "getWorld" -> currentWorld;
                case "getLocation" -> position.clone();
                case "playSound" -> {
                    sounds.add(((Sound) args[0]).name().asString());
                    yield null;
                }
                case "spawnParticle" -> {
                    particleCount += (int) args[2];
                    maxParticleCount = Math.max(maxParticleCount, (int) args[2]);
                    colors.add(((Particle.DustOptions) args[7]).getColor().asRGB());
                    yield null;
                }
                default -> throw new AssertionError(method.getName());
            });
        }

        private <T extends VexService> void install(Class<T> type, T value) throws Exception {
            var method = DefaultServiceRegistry.class.getDeclaredMethod("register", ServiceOwner.class,
                Class.class, VexService.class);
            method.setAccessible(true);
            method.invoke(registry, owner, type, value);
        }

        private Location origin() {
            return new Location(world, 4.5, 64.5, 0.5);
        }

        private ItemCollectionRequest request() {
            return new ItemCollectionRequest(origin(), new Stack(), valid::get, (x, y, z) -> null);
        }

        private void play() {
            service.play(player, request());
        }

        private void drain() {
            int count = 0;
            while (!regionJobs.isEmpty()) {
                assertTrue(++count < 10000);
                inRegion = true;
                try {
                    regionJobs.removeFirst().run();
                } finally {
                    inRegion = false;
                }
            }
        }

        private void tick(boolean drain) {
            ticks++;
            for (var timer : List.copyOf(timers)) {
                if (!timer.cancelled) {
                    timer.callback.run();
                }
            }
            if (drain) {
                drain();
            }
        }

        @Override
        public void close() throws Exception {
            try {
                services.unregisterOwnedServices();
            } finally {
                serverField.set(null, previousServer);
            }
        }
    }

    private static final class Timer implements VexTask {
        private final Runnable callback;
        private boolean cancelled;

        private Timer(Runnable callback) {
            this.callback = callback;
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

    private static final class Stack extends ItemStack {

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public void setAmount(int amount) {
        }

        @Override
        public ItemStack clone() {
            return new Stack();
        }
    }

    @Dependencies(ItemCollectionEffectService.class)
    public static final class CollectionConsumer {
        private final ItemCollectionEffectService service;

        public CollectionConsumer(VexServiceRegistry services) {
            service = services.require(ItemCollectionEffectService.class);
        }
    }
}
