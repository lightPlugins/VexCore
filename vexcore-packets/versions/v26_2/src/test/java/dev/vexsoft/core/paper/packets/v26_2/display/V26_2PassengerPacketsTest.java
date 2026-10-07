package dev.vexsoft.core.paper.packets.v26_2.display;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.StreamSupport;
import net.minecraft.SharedConstants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class V26_2PassengerPacketsTest {

    @BeforeAll
    static void initializeRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void appendsViewerPassengerWithoutChangingModelEnginePassengers() {
        ClientboundSetPassengersPacket original = mount(42, 10, 11);
        AtomicReference<List<Integer>> nativePassengers = new AtomicReference<>();
        ClientboundSetPassengersPacket rewritten = (ClientboundSetPassengersPacket) V26_2PassengerPackets.append(
            original, vehicle -> vehicle == 42 ? List.of(90, 91) : List.of(),
            (vehicle, passengers) -> nativePassengers.set(passengers));

        assertEquals(42, rewritten.getVehicle());
        assertEquals(List.of(10, 11), nativePassengers.get());
        assertArrayEquals(new int[]{10, 11, 90, 91}, rewritten.getPassengers());
        assertArrayEquals(new int[]{10, 11}, original.getPassengers());
        assertSame(original, V26_2PassengerPackets.append(original, vehicle -> List.of()));
    }

    @Test
    void rewritesNestedMountsAndAvoidsDuplicatePassengers() {
        ClientboundSetPassengersPacket relevant = mount(42, 10, 90);
        ClientboundSetPassengersPacket unrelated = mount(43, 20);
        ClientboundBundlePacket inner = new ClientboundBundlePacket(List.of(relevant));
        ClientboundBundlePacket bundle = new ClientboundBundlePacket(List.of(inner, unrelated));
        ClientboundBundlePacket rewritten = (ClientboundBundlePacket) V26_2PassengerPackets.append(bundle,
            vehicle -> vehicle == 42 ? List.of(90, 91) : List.of());

        List<Packet<? super ClientGamePacketListener>> packets = StreamSupport
            .stream(rewritten.subPackets().spliterator(), false).toList();
        List<Packet<? super ClientGamePacketListener>> innerPackets = StreamSupport
            .stream(((ClientboundBundlePacket) packets.getFirst()).subPackets().spliterator(), false).toList();
        assertArrayEquals(new int[]{10, 90, 91},
            ((ClientboundSetPassengersPacket) innerPackets.getFirst()).getPassengers());
        assertSame(unrelated, packets.getLast());
    }

    @Test
    void forgetsRemovedVirtualVehiclesInsideBundles() {
        ClientboundRemoveEntitiesPacket removal = new ClientboundRemoveEntitiesPacket(42, 43);
        ClientboundBundlePacket bundle = new ClientboundBundlePacket(List.of(removal));
        List<Integer> removed = new ArrayList<>();

        assertSame(bundle, V26_2PassengerPackets.append(bundle, vehicle -> List.of(),
            (vehicle, passengers) -> { }, removed::add));
        assertEquals(List.of(42, 43), removed);
    }

    @Test
    void restoresMountAfterStandaloneReplacementSpawn() {
        ClientboundAddEntityPacket spawn = spawn(42);
        Object rewritten = V26_2PassengerPackets.append(spawn, vehicle -> List.of(),
            (vehicle, passengers) -> { }, ignored -> List.of(),
            entity -> entity == 42 ? Map.of(42, List.of(10, 90)) : Map.of(), () -> { });
        List<Packet<? super ClientGamePacketListener>> contents = contents(rewritten);
        assertEquals(2, contents.size());
        assertSame(spawn, contents.getFirst());
        ClientboundSetPassengersPacket mount = assertInstanceOf(ClientboundSetPassengersPacket.class,
            contents.getLast());
        assertEquals(42, mount.getVehicle());
        assertArrayEquals(new int[] {10, 90}, mount.getPassengers());
    }

    @Test
    void repairsBundledSpawnsInPlaceAndProcessesRemovalFirst() {
        ClientboundRemoveEntitiesPacket removal = new ClientboundRemoveEntitiesPacket(42);
        ClientboundAddEntityPacket pivot = spawn(42);
        ClientboundAddEntityPacket passenger = spawn(90);
        List<String> observed = new ArrayList<>();
        ClientboundBundlePacket bundle = new ClientboundBundlePacket(List.of(removal, pivot, passenger));
        Object rewritten = V26_2PassengerPackets.append(bundle, vehicle -> List.of(),
            (vehicle, passengers) -> { }, entity -> {
                observed.add("remove:" + entity);
                return List.of();
            }, entity -> {
                observed.add("spawn:" + entity);
                return entity == 90 ? Map.of(42, List.of(10, 90)) : Map.of();
            }, () -> { });
        List<Packet<? super ClientGamePacketListener>> contents = contents(rewritten);
        assertEquals(List.of("remove:42", "spawn:42", "spawn:90"), observed);
        assertEquals(4, contents.size());
        assertSame(removal, contents.get(0));
        assertSame(pivot, contents.get(1));
        assertSame(passenger, contents.get(2));
        assertInstanceOf(ClientboundSetPassengersPacket.class, contents.get(3));
    }

    @Test
    void worldResetRunsBeforeFollowingSpawnsWithoutAddingIdlePackets() {
        ClientboundRespawnPacket reset = new ClientboundRespawnPacket(null, (byte) 0);
        ClientboundAddEntityPacket spawn = spawn(42);
        List<String> observed = new ArrayList<>();
        ClientboundBundlePacket bundle = new ClientboundBundlePacket(List.of(reset, spawn));
        assertSame(bundle, V26_2PassengerPackets.append(bundle, vehicle -> List.of(),
            (vehicle, passengers) -> { }, ignored -> List.of(), entity -> {
                observed.add("spawn:" + entity);
                return Map.of();
            }, () -> observed.add("reset")));
        assertEquals(List.of("reset", "spawn:42"), observed);
    }

    @Test
    void removesViewerDisplaysAlongsideTheirPivotWithoutChangingOtherEntities() {
        ClientboundRemoveEntitiesPacket removal = new ClientboundRemoveEntitiesPacket(42, 43);
        Object rewritten = V26_2PassengerPackets.append(removal, vehicle -> List.of(),
            (vehicle, passengers) -> { }, entity -> entity == 42 ? List.of(90, 91) : List.of(),
            ignored -> Map.of(), () -> { });
        assertEquals(List.of(42, 43, 90, 91),
            assertInstanceOf(ClientboundRemoveEntitiesPacket.class, rewritten).getEntityIds());
        assertEquals(List.of(42, 43), removal.getEntityIds());
    }

    private static ClientboundAddEntityPacket spawn(final int id) {
        return new ClientboundAddEntityPacket(id, UUID.randomUUID(), 0, 64, 0, 0, 0,
            EntityTypes.TEXT_DISPLAY, 0, Vec3.ZERO, 0);
    }

    private static List<Packet<? super ClientGamePacketListener>> contents(final Object packet) {
        return StreamSupport.stream(assertInstanceOf(ClientboundBundlePacket.class, packet)
            .subPackets().spliterator(), false).toList();
    }

    private static ClientboundSetPassengersPacket mount(final int vehicle, final int... passengers) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeVarInt(vehicle);
            buffer.writeVarIntArray(passengers);
            return ClientboundSetPassengersPacket.STREAM_CODEC.decode(buffer);
        } finally {
            buffer.release();
        }
    }
}
