package dev.vexsoft.core.paper.service.packets.v26_2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.packets.block.VirtualBlock;
import dev.vexsoft.core.paper.packets.internal.OutboundPacketBatch;
import dev.vexsoft.core.paper.packets.service.VirtualBlockService;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockChangedAckPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.bukkit.craftbukkit.block.data.CraftBlockData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Tests wire-level ordering that prevents a predicted instant break from resurrecting real wheat. */
final class VexVirtualBlockPacketAdapterServiceTest {
    private static final BlockPos POSITION = new BlockPos(-17, 70, 32);
    private final UUID viewer = UUID.randomUUID();
    private final List<Integer> predictions = new ArrayList<>();
    private List<VirtualBlock> acknowledgements = List.of();
    private List<VirtualBlock> projections = List.of();

    @BeforeAll
    static void initializeRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void everyOriginalCorrectionIsReplacedAndSharedPacketIsUnmodified() {
        projections = List.of(wheat());
        var original = new ClientboundBlockUpdatePacket(POSITION, Blocks.WHEAT.defaultBlockState());
        var rewritten = assertInstanceOf(ClientboundBlockUpdatePacket.class, adapter().rewrite(viewer, original));
        assertSame(Blocks.AIR.defaultBlockState(), rewritten.getBlockState());
        assertSame(Blocks.WHEAT.defaultBlockState(), original.getBlockState());
        var unrelated = new ClientboundBlockUpdatePacket(POSITION.above(), Blocks.STONE.defaultBlockState());
        assertSame(unrelated, adapter().rewrite(viewer, unrelated));
    }

    @Test
    void airReachesPredictionStateBeforeItsAcknowledgementWithoutAnotherTick() {
        acknowledgements = List.of(wheat());
        var ack = new ClientboundBlockChangedAckPacket(7);
        var packets = contents(adapter().rewrite(viewer, ack));
        assertEquals(2, packets.size());
        assertSame(Blocks.AIR.defaultBlockState(),
            assertInstanceOf(ClientboundBlockUpdatePacket.class, packets.getFirst()).getBlockState());
        assertSame(ack, packets.getLast());
        // With no active projection, the ordinary Vanilla acknowledgement stays untouched.
        acknowledgements = List.of();
        assertSame(ack, adapter().rewrite(viewer, ack));
    }

    @Test
    void capturesBreakAndRightClickSequencesWithoutDroppingTheServerAcknowledgement() {
        var adapter = adapter();
        adapter.predict(viewer, new ServerboundPlayerActionPacket(
            ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, POSITION, Direction.UP, 8));
        adapter.predict(viewer, new ServerboundUseItemOnPacket(InteractionHand.OFF_HAND,
            new BlockHitResult(Vec3.atCenterOf(POSITION), Direction.UP, POSITION, false), 9));
        adapter.predict(viewer, new ServerboundPlayerActionPacket(
            ServerboundPlayerActionPacket.Action.DROP_ITEM, BlockPos.ZERO, Direction.DOWN, 0));
        assertEquals(List.of(8, 9), predictions);
    }

    @Test
    void sectionCorrectionsSubstituteOnlyOwnedProjectionAndLeaveSourceIntact() {
        projections = List.of(wheat());
        var states = new Short2ObjectOpenHashMap<BlockState>();
        states.put(SectionPos.sectionRelativePos(POSITION), Blocks.WHEAT.defaultBlockState());
        states.put(SectionPos.sectionRelativePos(POSITION.above()), Blocks.STONE.defaultBlockState());
        var original = new ClientboundSectionBlocksUpdatePacket(SectionPos.of(POSITION), states);
        var rewritten = assertInstanceOf(ClientboundSectionBlocksUpdatePacket.class, adapter().rewrite(viewer, original));
        rewritten.runUpdates((position, state) -> assertSame(position.equals(POSITION)
            ? Blocks.AIR.defaultBlockState() : Blocks.STONE.defaultBlockState(), state));
        original.runUpdates((position, state) -> assertSame(position.equals(POSITION)
            ? Blocks.WHEAT.defaultBlockState() : Blocks.STONE.defaultBlockState(), state));
    }

    @Test
    void chunkAndProjectionAreProcessedInOneFlatBundleBeforeRendering() {
        projections = List.of(wheat());
        var original = chunk(-2, 2);
        var packets = contents(adapter().rewrite(viewer, original));
        assertEquals(2, packets.size());
        assertSame(original, packets.getFirst());
        assertInstanceOf(ClientboundSectionBlocksUpdatePacket.class, packets.getLast())
            .runUpdates((position, state) -> {
                assertEquals(POSITION, position);
                assertSame(Blocks.AIR.defaultBlockState(), state);
            });
        projections = List.of();
        assertSame(original, adapter().rewrite(viewer, original));
    }

    @Test
    void fullProtocolBundlesSplitIntoOrderedFlatBoundedWrites() {
        acknowledgements = List.of(wheat());
        var ordinary = new ClientboundBlockUpdatePacket(POSITION.above(), Blocks.STONE.defaultBlockState());
        List<Packet<? super ClientGamePacketListener>> source = new ArrayList<>(Collections.nCopies(4095, ordinary));
        var ack = new ClientboundBlockChangedAckPacket(11);
        source.add(ack);
        var batch = assertInstanceOf(OutboundPacketBatch.class,
            adapter().rewrite(viewer, new ClientboundBundlePacket(source)));
        assertEquals(2, batch.packets().size());
        assertEquals(4096, contents(batch.packets().getFirst()).size());
        assertSame(ack, contents(batch.packets().getLast()).getFirst());
        for (Object bundle : batch.packets()) {
            for (var packet : contents(bundle)) {
                assertFalse(packet instanceof ClientboundBundlePacket);
            }
        }
    }

    private VexVirtualBlockPacketAdapterService adapter() {
        var blocks = (VirtualBlockService) Proxy.newProxyInstance(VirtualBlockService.class.getClassLoader(),
            new Class<?>[]{VirtualBlockService.class}, (instance, method, arguments) -> switch (method.getName()) {
                case "find" -> projections.stream().filter(block -> block.x() == (int) arguments[1]
                    && block.y() == (int) arguments[2] && block.z() == (int) arguments[3]).findFirst().orElse(null);
                case "chunk" -> projections;
                case "acknowledge" -> acknowledgements;
                case "predict" -> {
                    assertEquals(viewer, arguments[0]);
                    assertEquals(POSITION.getX(), arguments[2]);
                    assertEquals(POSITION.getY(), arguments[3]);
                    assertEquals(POSITION.getZ(), arguments[4]);
                    predictions.add((Integer) arguments[1]);
                    yield null;
                }
                default -> null;
            });
        var registry = (VexServiceRegistry) Proxy.newProxyInstance(VexServiceRegistry.class.getClassLoader(),
            new Class<?>[]{VexServiceRegistry.class}, (instance, method, arguments) -> blocks);
        return new VexVirtualBlockPacketAdapterService(registry);
    }

    private static VirtualBlock wheat() {
        return new VirtualBlock(POSITION.getX(), POSITION.getY(), POSITION.getZ(),
            CraftBlockData.createData(Blocks.WHEAT.defaultBlockState()), CraftBlockData.createData(Blocks.AIR.defaultBlockState()));
    }

    private static List<Packet<? super ClientGamePacketListener>> contents(final Object packet) {
        List<Packet<? super ClientGamePacketListener>> contents = new ArrayList<>();
        assertInstanceOf(ClientboundBundlePacket.class, packet).subPackets().forEach(contents::add);
        assertTrue(contents.size() <= 4096);
        return contents;
    }

    private static ClientboundLevelChunkWithLightPacket chunk(final int chunkX, final int chunkZ) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            buffer.writeInt(chunkX);
            buffer.writeInt(chunkZ);
            buffer.writeVarInt(0);
            buffer.writeVarInt(0);
            buffer.writeVarInt(0);
            for (int index = 0; index < 4; index++) {
                buffer.writeBitSet(new BitSet());
            }
            buffer.writeVarInt(0);
            buffer.writeVarInt(0);
            return ClientboundLevelChunkWithLightPacket.STREAM_CODEC.decode(buffer);
        } finally {
            buffer.release();
        }
    }
}
