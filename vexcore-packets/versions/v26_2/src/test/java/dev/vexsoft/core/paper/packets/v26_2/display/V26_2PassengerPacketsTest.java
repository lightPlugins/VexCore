package dev.vexsoft.core.paper.packets.v26_2.display;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.StreamSupport;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import org.junit.jupiter.api.Test;

class V26_2PassengerPacketsTest {

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
