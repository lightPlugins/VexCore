package dev.vexsoft.core.paper.service.packets.v26_2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import dev.vexsoft.core.paper.packets.internal.PacketDuplexHandler;
import dev.vexsoft.core.paper.packets.v26_2.display.V26_2PassengerPackets;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Exercises the outbound pipeline order used by ModelEngine's protected model packets. */
public final class VexPacketConnectionAdapterServiceTest {

    @BeforeAll
    static void initializeRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void observesProtectedModelSpawnsAfterRespawnInEitherInjectionOrder() {
        for (boolean modelEngineFirst : List.of(true, false)) {
            var channel = new EmbeddedChannel();
            try {
                channel.pipeline().addLast("packet_handler", new ChannelDuplexHandler());
                var observed = new ArrayList<String>();
                var handler = observer(observed);
                if (modelEngineFirst) {
                    installModelEngine(channel);
                }
                VexPacketConnectionAdapterService.addHandler(channel.pipeline(), UUID.randomUUID(), handler);
                if (!modelEngineFirst) {
                    installModelEngine(channel);
                }
                var reset = new ClientboundRespawnPacket(null, (byte) 0);
                channel.writeOutbound(reset);
                assertSame(reset, channel.readOutbound());

                var spawn = new ClientboundAddEntityPacket(42, UUID.randomUUID(), 0, 64, 0, 0, 0,
                    EntityTypes.ARMOR_STAND, 0, Vec3.ZERO, 0);
                var bundle = new ClientboundBundlePacket(List.of(spawn));
                channel.writeOutbound(new ProtectedModelPacket(bundle));
                assertSame(bundle, channel.readOutbound());
                assertEquals(List.of("reset", "spawn:42"), observed);
            } finally {
                channel.finishAndReleaseAll();
            }
        }
    }

    @Test
    void rewrittenProtectedModelPacketReachesClientAsNativePacket() {
        var channel = new EmbeddedChannel();
        try {
            channel.pipeline().addLast("packet_handler", new ChannelDuplexHandler());
            installModelEngine(channel);
            var observed = new ArrayList<String>();
            VexPacketConnectionAdapterService.addHandler(channel.pipeline(), UUID.randomUUID(), new PacketDuplexHandler() {
                @Override
                public Object write(final UUID viewerId, final Object packet) {
                    observed.add(packet.getClass().getSimpleName());
                    return "rewritten";
                }

                @Override
                public Object read(final UUID viewerId, final Object packet) {
                    return packet;
                }
            });
            channel.writeOutbound(new ProtectedModelPacket("model"));
            assertEquals("rewritten", channel.readOutbound());
            assertEquals(List.of("String"), observed);
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void reinjectionKeepsOneHandlerAndUnwrappedPacketsWorkWithoutModelEngine() {
        var channel = new EmbeddedChannel();
        try {
            channel.pipeline().addLast("packet_handler", new ChannelDuplexHandler());
            var observed = new ArrayList<String>();
            UUID viewerId = UUID.randomUUID();
            var handler = observer(observed);
            VexPacketConnectionAdapterService.addHandler(channel.pipeline(), viewerId, handler);
            VexPacketConnectionAdapterService.addHandler(channel.pipeline(), viewerId, handler);
            var reset = new ClientboundRespawnPacket(null, (byte) 0);
            channel.writeOutbound(reset);
            assertSame(reset, channel.readOutbound());
            assertEquals(List.of("reset"), observed);
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    private static PacketDuplexHandler observer(final List<String> observed) {
        return new PacketDuplexHandler() {
            @Override
            public Object write(final UUID viewerId, final Object packet) {
                return V26_2PassengerPackets.append(packet, ignored -> List.of(),
                    (vehicle, passengers) -> { }, ignored -> List.of(), entity -> {
                        observed.add("spawn:" + entity);
                        return Map.of();
                    }, () -> observed.add("reset"));
            }

            @Override
            public Object read(final UUID viewerId, final Object packet) {
                return packet;
            }
        };
    }

    private static void installModelEngine(final EmbeddedChannel channel) {
        // ModelEngine installs the unpacker before its own handler; outbound traversal is reversed.
        channel.pipeline().addBefore("packet_handler", "protected_packet_unpacker", new ChannelDuplexHandler() {
            @Override
            public void write(final ChannelHandlerContext context, final Object message,
                              final ChannelPromise promise) throws Exception {
                super.write(context, message instanceof ProtectedModelPacket protectedPacket
                    ? protectedPacket.packet() : message, promise);
            }
        });
        channel.pipeline().addBefore("packet_handler", "model_engine_packet_handler", new ChannelDuplexHandler());
    }

    private record ProtectedModelPacket(Object packet) {
    }
}
