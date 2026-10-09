package dev.vexsoft.core.paper.service.packets.v26_2;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.packets.block.VirtualBlock;
import dev.vexsoft.core.paper.packets.internal.OutboundPacketBatch;
import dev.vexsoft.core.paper.packets.service.VirtualBlockPacketAdapterService;
import dev.vexsoft.core.paper.packets.service.VirtualBlockService;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ClientboundBlockChangedAckPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.craftbukkit.block.data.CraftBlockData;

/** Rewrites corrections before transmission and virtual prediction states before acknowledgements. */
@Dependencies(VirtualBlockService.class)
public final class VexVirtualBlockPacketAdapterService implements VirtualBlockPacketAdapterService {
    private final VirtualBlockService blocks;

    public VexVirtualBlockPacketAdapterService(final VexServiceRegistry services) {
        blocks = services.require(VirtualBlockService.class);
    }

    @Override
    public Object rewrite(final UUID viewer, final Object packet) {
        if (!(packet instanceof Packet<?>)) {
            return packet;
        }
        List<Packet<? super ClientGamePacketListener>> result = new ArrayList<>();
        append(viewer, packet, result);
        if (result.size() <= 4096) {
            return result.size() == 1 ? result.getFirst() : new ClientboundBundlePacket(result);
        }
        List<Object> batches = new ArrayList<>();
        for (int start = 0; start < result.size(); start += 4096) {
            batches.add(new ClientboundBundlePacket(List.copyOf(result.subList(start,
                Math.min(start + 4096, result.size())))));
        }
        return new OutboundPacketBatch(batches);
    }

    @Override
    public void predict(final UUID viewer, final Object packet) {
        if (packet instanceof ServerboundPlayerActionPacket action
            && (action.getAction() == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK
                || action.getAction() == ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK
                || action.getAction() == ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK)) {
            BlockPos position = action.getPos();
            blocks.predict(viewer, action.getSequence(), position.getX(), position.getY(), position.getZ());
        } else if (packet instanceof ServerboundUseItemOnPacket action) {
            BlockPos position = action.getHitResult().getBlockPos();
            blocks.predict(viewer, action.getSequence(), position.getX(), position.getY(), position.getZ());
        }
    }

    private void append(
        final UUID viewer,
        final Object packet,
        final List<Packet<? super ClientGamePacketListener>> result
    ) {
        if (packet instanceof ClientboundBundlePacket bundle) {
            bundle.subPackets().forEach(nested -> append(viewer, nested, result));
            return;
        }
        if (packet instanceof ClientboundRespawnPacket respawn) {
            blocks.bind(viewer, respawn.commonPlayerSpawnInfo().dimension().identifier().toString());
            result.add(respawn);
            return;
        }
        if (packet instanceof ClientboundLoginPacket login) {
            blocks.bind(viewer, login.commonPlayerSpawnInfo().dimension().identifier().toString());
            result.add(login);
            return;
        }
        if (packet instanceof ClientboundBlockUpdatePacket update) {
            BlockPos position = update.getPos();
            VirtualBlock block = blocks.find(viewer, position.getX(), position.getY(), position.getZ());
            result.add(block == null ? update : correction(block));
            return;
        }
        if (packet instanceof ClientboundBlockChangedAckPacket acknowledgement) {
            // Updating prediction's retained state BEFORE ack avoids an original-block flash at instant breaks.
            blocks.acknowledge(viewer, acknowledgement.sequence()).forEach(block -> result.add(correction(block)));
            result.add(acknowledgement);
            return;
        }
        if (packet instanceof ClientboundSectionBlocksUpdatePacket section) {
            var changes = new Short2ObjectOpenHashMap<BlockState>();
            SectionPos[] owner = {null};
            section.runUpdates((position, state) -> {
                owner[0] = SectionPos.of(position);
                VirtualBlock block = blocks.find(viewer, position.getX(), position.getY(), position.getZ());
                changes.put(SectionPos.sectionRelativePos(position), block == null ? state
                    : ((CraftBlockData) block.replacement()).getState());
            });
            result.add(owner[0] == null ? section : new ClientboundSectionBlocksUpdatePacket(owner[0], changes));
            return;
        }
        @SuppressWarnings("unchecked")
        Packet<? super ClientGamePacketListener> nativePacket = (Packet<? super ClientGamePacketListener>) packet;
        result.add(nativePacket);
        if (packet instanceof ClientboundLevelChunkWithLightPacket chunk) {
            // A flat bundle applies the base chunk and its projections before the client renders it.
            var sections = new LinkedHashMap<SectionPos, Short2ObjectOpenHashMap<BlockState>>();
            for (VirtualBlock block : blocks.chunk(viewer, chunk.getX(), chunk.getZ())) {
                BlockPos position = new BlockPos(block.x(), block.y(), block.z());
                sections.computeIfAbsent(SectionPos.of(position), ignored ->
                    new Short2ObjectOpenHashMap<>())
                    .put(SectionPos.sectionRelativePos(position), ((CraftBlockData) block.replacement()).getState());
            }
            sections.forEach((position, states) -> result.add(new ClientboundSectionBlocksUpdatePacket(position, states)));
        }
    }

    private static ClientboundBlockUpdatePacket correction(final VirtualBlock block) {
        return new ClientboundBlockUpdatePacket(new BlockPos(block.x(), block.y(), block.z()),
            ((CraftBlockData) block.replacement()).getState());
    }
}
