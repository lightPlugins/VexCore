package dev.vexsoft.core.paper.packets.v26_2.interactiveui;

import dev.vexsoft.core.paper.packets.interactiveui.InteractiveUiPacketTransport;
import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.level.block.Blocks;

/** Protects one viewer-local block using packet contents only, without changing world state or unrelated updates. */
@UtilityClass
public class V26_2InteractiveUiTargetPreserver {

    private static final int MAX_BUNDLE_PACKETS = 4096;

    public static Object preserve(final InteractiveUiPacketTransport transport, final Object packet) {
        BlockPos target = new BlockPos(transport.getBlockX(), transport.getBlockY(), transport.getBlockZ());

        if (packet instanceof ClientboundBlockUpdatePacket update) {
            return target.equals(update.getPos()) ? correction(target) : packet;
        }

        if (packet instanceof ClientboundSectionBlocksUpdatePacket section && contains(section, target)) {
            return new ClientboundBundlePacket(List.of(section, correction(target)));
        }

        if (packet instanceof ClientboundLevelChunkWithLightPacket chunk && contains(chunk, target)) {
            return new ClientboundBundlePacket(List.of(chunk, correction(target)));
        }

        if (packet instanceof ClientboundBundlePacket bundle) {
            List<Packet<? super ClientGamePacketListener>> contents = new ArrayList<>();

            if (!flatten(bundle, contents)) {
                return packet;
            }

            boolean changed = false;
            boolean needsCorrection = false;

            for (int index = 0; index < contents.size(); index++) {
                Packet<? super ClientGamePacketListener> nested = contents.get(index);

                if (nested instanceof ClientboundBlockUpdatePacket update && target.equals(update.getPos())) {
                    contents.set(index, correction(target));
                    changed = true;
                } else if (nested instanceof ClientboundSectionBlocksUpdatePacket section && contains(section, target)
                    || nested instanceof ClientboundLevelChunkWithLightPacket chunk && contains(chunk, target)) {
                    needsCorrection = true;
                }
            }

            // Never exceed the client's bundle limit. Full third-party bulk bundles require a later target refresh.
            if (needsCorrection && contents.size() < MAX_BUNDLE_PACKETS) {
                contents.add(correction(target));
                changed = true;
            }

            return changed ? new ClientboundBundlePacket(contents) : packet;
        }

        return packet;
    }

    private static boolean contains(final ClientboundSectionBlocksUpdatePacket section, final BlockPos target) {
        boolean[] containsTarget = {false};

        section.runUpdates((position, state) -> {
            if (target.equals(position)) {
                containsTarget[0] = true;
            }
        });
        return containsTarget[0];
    }

    private static boolean contains(final ClientboundLevelChunkWithLightPacket chunk, final BlockPos target) {
        return chunk.getX() == (target.getX() >> 4) && chunk.getZ() == (target.getZ() >> 4);
    }

    private static ClientboundBlockUpdatePacket correction(final BlockPos target) {
        return new ClientboundBlockUpdatePacket(target, Blocks.BEDROCK.defaultBlockState());
    }

    private static boolean flatten(
        final ClientboundBundlePacket bundle,
        final List<Packet<? super ClientGamePacketListener>> contents
    ) {
        for (Packet<? super ClientGamePacketListener> packet : bundle.subPackets()) {
            if (packet instanceof ClientboundBundlePacket nested) {
                if (!flatten(nested, contents)) {
                    return false;
                }
            } else {
                if (contents.size() == MAX_BUNDLE_PACKETS) {
                    return false;
                }

                contents.add(packet);
            }
        }

        return true;
    }
}
