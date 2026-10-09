package dev.vexsoft.core.paper.service.packets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.paper.packets.block.VirtualBlock;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.packets.service.PacketConnectionAdapterService;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

/** Verifies published projections, reconnects and prediction bookkeeping without loading any chunks. */
final class VexVirtualBlockServiceTest {
    @Test
    void joiningBeforeChunkLoaderInitializationRetainsOverlaysForInitialChunkPackets() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.ready = false;
            fixture.service.synchronize(fixture.alice);
            fixture.service.put("minecraft:overworld", null, fixture.block());
            assertTrue(fixture.aliceUpdates.isEmpty());
            assertTrue(fixture.bobUpdates.isEmpty());
            assertEquals(1, fixture.service.chunk(fixture.alice.getUniqueId(), 0, 0).size());
            fixture.ready = true;
            fixture.service.synchronize(fixture.alice);
            assertEquals(List.of(fixture.air), fixture.aliceUpdates);
        }
    }

    @Test
    void cooldownExpiryAlsoToleratesAnUninitializedChunkLoader() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.service.put("minecraft:overworld", null, fixture.block());
            fixture.ready = false;
            fixture.service.remove("minecraft:overworld", null, 1, 64, 1);
            fixture.service.synchronize(fixture.alice);
            assertTrue(fixture.service.chunk(fixture.alice.getUniqueId(), 0, 0).isEmpty());
            assertEquals(List.of(fixture.air), fixture.aliceUpdates);
            assertEquals(0, fixture.reads);
        }
    }

    @Test
    void globalProjectionReachesEveryViewerAndPersonalProjectionOnlyItsOwner() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.service.put("minecraft:overworld", fixture.alice.getUniqueId(), fixture.block());
            assertEquals(List.of(fixture.air), fixture.aliceUpdates);
            assertTrue(fixture.bobUpdates.isEmpty());
            assertNull(fixture.service.find(fixture.bob.getUniqueId(), 1, 64, 1));
            fixture.service.put("minecraft:overworld", null, fixture.block());
            assertEquals(List.of(fixture.air), fixture.bobUpdates);
            assertSame(fixture.air, fixture.service.find(fixture.bob.getUniqueId(), 1, 64, 1).replacement());
            fixture.service.remove("minecraft:overworld", null, 1, 64, 1);
            assertSame(fixture.air, fixture.aliceUpdates.getLast());
            assertSame(fixture.original, fixture.bobUpdates.getLast());
        }
    }

    @Test
    void reconnectAndWorldChangeRetainPersonalProjectionButDiscardStalePredictions() throws Exception {
        try (var fixture = new Fixture()) {
            UUID owner = fixture.alice.getUniqueId();
            fixture.service.put("minecraft:overworld", owner, fixture.block());
            fixture.service.predict(owner, 1, 1, 64, 1);
            fixture.service.disconnect(owner);
            fixture.service.bind(owner, "minecraft:overworld");
            assertEquals(1, fixture.service.chunk(owner, 0, 0).size());
            assertTrue(fixture.service.acknowledge(owner, 1).isEmpty());
            fixture.service.bind(owner, "minecraft:the_nether");
            assertTrue(fixture.service.chunk(owner, 0, 0).isEmpty());
            fixture.service.bind(owner, "minecraft:overworld");
            assertEquals(1, fixture.service.chunk(owner, 0, 0).size());
        }
    }

    @Test
    void acknowledgementDrainsOnlyPredictionsUpToSequenceAndResolvesCurrentOverlay() throws Exception {
        try (var fixture = new Fixture()) {
            UUID owner = fixture.alice.getUniqueId();
            fixture.service.put("minecraft:overworld", owner, fixture.block());
            fixture.service.predict(owner, 7, 1, 64, 1);
            fixture.service.predict(owner, 8, 1, 64, 1);
            assertEquals(1, fixture.service.acknowledge(owner, 7).size());
            assertTrue(fixture.service.acknowledge(owner, 7).isEmpty());
            assertEquals(1, fixture.service.acknowledge(owner, 8).size());
            fixture.service.predict(owner, 9, 1, 64, 1);
            fixture.service.remove("minecraft:overworld", owner, 1, 64, 1);
            assertTrue(fixture.service.acknowledge(owner, 9).isEmpty());
        }
    }

    @Test
    void expiryUsesCurrentWorldStateAndNeverLoadsAnUnloadedChunk() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.service.put("minecraft:overworld", null, fixture.block());
            fixture.original = fixture.air;
            fixture.service.remove("minecraft:overworld", null, 1, 64, 1);
            assertSame(fixture.air, fixture.bobUpdates.getLast());
            fixture.loaded = false;
            fixture.service.put("minecraft:overworld", null, fixture.block());
            int reads = fixture.reads;
            fixture.service.remove("minecraft:overworld", null, 1, 64, 1);
            assertEquals(reads, fixture.reads);
        }
    }

    private static <T> T proxy(final Class<T> type, final InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    private static final class Fixture implements AutoCloseable {
        private final Field serverField;
        private final Object previousServer;
        private final VexVirtualBlockService service;
        private final BlockData air = data();
        private BlockData original = data();
        private final List<BlockData> aliceUpdates = new ArrayList<>();
        private final List<BlockData> bobUpdates = new ArrayList<>();
        private final Player alice;
        private final Player bob;
        private boolean loaded = true;
        private boolean ready = true;
        private int reads;

        private Fixture() throws Exception {
            var connections = proxy(PacketConnectionAdapterService.class, (instance, method, arguments) ->
                switch (method.getName()) {
                    case "getSentChunkKeys" -> ready ? Set.of(0L) : Set.of();
                    case "isChunkSent" -> ready && (long) arguments[1] == 0L;
                    default -> throw new AssertionError(method.getName());
                });
            service = new VexVirtualBlockService(proxy(VexServiceRegistry.class, (instance, method, arguments) -> {
                assertEquals(PacketConnectionAdapterService.class, arguments[0]);
                return connections;
            }));
            World world = proxy(World.class, (instance, method, arguments) -> switch (method.getName()) {
                case "getKey" -> NamespacedKey.minecraft("overworld");
                case "isChunkLoaded" -> loaded;
                case "getBlockAt" -> {
                    assertTrue(loaded);
                    reads++;
                    yield proxy(Block.class, (block, call, parameters) -> original);
                }
                default -> throw new AssertionError(method.getName());
            });
            alice = viewer(world, aliceUpdates);
            bob = viewer(world, bobUpdates);
            serverField = Bukkit.class.getDeclaredField("server");
            serverField.setAccessible(true);
            previousServer = serverField.get(null);
            serverField.set(null, proxy(Server.class, (instance, method, arguments) -> {
                assertEquals("getOnlinePlayers", method.getName());
                return List.of(alice, bob);
            }));
        }

        private VirtualBlock block() {
            return new VirtualBlock(1, 64, 1, original, air);
        }

        private static Player viewer(final World world, final List<BlockData> updates) {
            UUID id = UUID.randomUUID();
            return proxy(Player.class, (instance, method, arguments) -> switch (method.getName()) {
                case "getUniqueId" -> id;
                case "getWorld" -> world;
                case "getSentChunkKeys" -> throw new AssertionError("Unsafe Bukkit chunk-loader access");
                case "sendBlockChange" -> { updates.add((BlockData) arguments[1]); yield null; }
                default -> throw new AssertionError(method.getName());
            });
        }

        private static BlockData data() {
            return proxy(BlockData.class, (instance, method, arguments) -> switch (method.getName()) {
                case "clone" -> instance;
                case "equals" -> instance == arguments[0];
                case "toString" -> "block";
                default -> throw new AssertionError(method.getName());
            });
        }

        @Override
        public void close() throws Exception {
            try {
                service.close();
            } finally {
                serverField.set(null, previousServer);
            }
        }
    }
}
