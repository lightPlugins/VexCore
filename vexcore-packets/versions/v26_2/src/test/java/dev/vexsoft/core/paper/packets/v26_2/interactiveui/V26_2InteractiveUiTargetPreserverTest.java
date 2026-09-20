package dev.vexsoft.core.paper.packets.v26_2.interactiveui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import dev.vexsoft.core.paper.packets.interactiveui.InteractiveUiPacketTransport;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class V26_2InteractiveUiTargetPreserverTest {

    private static final BlockPos TARGET = new BlockPos(-17, 70, 32);
    private static final InteractiveUiPacketTransport TRANSPORT = new InteractiveUiPacketTransport(
        UUID.randomUUID(), UUID.randomUUID(), 11, 12, -17, 70, 32, -16.5, 70.5, 32.5, 0.0F, 0.0F
    );

    @BeforeAll
    static void initializeRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void substitutesOnlyTheExactSingleBlock() {
        ClientboundBlockUpdatePacket original = new ClientboundBlockUpdatePacket(TARGET, Blocks.AIR.defaultBlockState());
        ClientboundBlockUpdatePacket rewritten = assertInstanceOf(
            ClientboundBlockUpdatePacket.class, preserve(original)
        );
        ClientboundBlockUpdatePacket unrelated = new ClientboundBlockUpdatePacket(
            TARGET.above(), Blocks.STONE.defaultBlockState()
        );

        assertEquals(TARGET, rewritten.getPos());
        assertSame(Blocks.BEDROCK.defaultBlockState(), rewritten.getBlockState());
        assertSame(Blocks.AIR.defaultBlockState(), original.getBlockState());
        assertSame(unrelated, preserve(unrelated));
    }

    @Test
    void preservesTheWholeSectionPacketAndAppendsOnlyOneCorrection() {
        ClientboundSectionBlocksUpdatePacket original = section(TARGET, TARGET.above());
        List<Packet<? super ClientGamePacketListener>> rewritten = contents(preserve(original));

        assertEquals(2, rewritten.size());
        assertSame(original, rewritten.getFirst());
        assertEquals(TARGET, assertInstanceOf(ClientboundBlockUpdatePacket.class, rewritten.getLast()).getPos());
        ClientboundSectionBlocksUpdatePacket unrelated = section(TARGET.above());

        assertSame(unrelated, preserve(unrelated));
    }

    @Test
    void preservesMatchingChunkPayloadAndDoesNotTouchOtherChunks() {
        ClientboundLevelChunkWithLightPacket original = chunk(-2, 2);
        List<Packet<? super ClientGamePacketListener>> rewritten = contents(preserve(original));

        assertEquals(2, rewritten.size());
        assertSame(original, rewritten.getFirst());
        assertEquals(TARGET, assertInstanceOf(ClientboundBlockUpdatePacket.class, rewritten.getLast()).getPos());
        ClientboundLevelChunkWithLightPacket unrelated = chunk(-1, 2);

        assertSame(unrelated, preserve(unrelated));
    }

    @Test
    void existingBundlesRemainFlatAndNeverExceedTheProtocolLimit() {
        ClientboundSectionBlocksUpdatePacket section = section(TARGET);
        ClientboundBlockUpdatePacket unrelated = new ClientboundBlockUpdatePacket(
            TARGET.above(), Blocks.AIR.defaultBlockState()
        );
        ClientboundBundlePacket original = new ClientboundBundlePacket(List.of(unrelated, section));
        List<Packet<? super ClientGamePacketListener>> rewritten = contents(preserve(original));

        assertEquals(3, rewritten.size());
        assertSame(unrelated, rewritten.getFirst());
        assertSame(section, rewritten.get(1));
        assertInstanceOf(ClientboundBlockUpdatePacket.class, rewritten.getLast());

        ClientboundBundlePacket full = new ClientboundBundlePacket(Collections.nCopies(4096, section));

        assertSame(full, preserve(full));
    }

    private static Object preserve(final Object packet) {
        return V26_2InteractiveUiTargetPreserver.preserve(TRANSPORT, packet);
    }

    private static List<Packet<? super ClientGamePacketListener>> contents(final Object packet) {
        List<Packet<? super ClientGamePacketListener>> contents = new ArrayList<>();

        assertInstanceOf(ClientboundBundlePacket.class, packet).subPackets().forEach(contents::add);
        return contents;
    }

    private static ClientboundSectionBlocksUpdatePacket section(final BlockPos... positions) {
        Short2ObjectOpenHashMap<BlockState> changes = new Short2ObjectOpenHashMap<>();

        for (BlockPos position : positions) {
            changes.put(SectionPos.sectionRelativePos(position), Blocks.AIR.defaultBlockState());
        }

        return new ClientboundSectionBlocksUpdatePacket(SectionPos.of(TARGET), changes);
    }

    private static ClientboundLevelChunkWithLightPacket chunk(final int chunkX, final int chunkZ) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);

        try {
            // Empty wire payload is sufficient to exercise coordinate filtering without creating a server world.
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
