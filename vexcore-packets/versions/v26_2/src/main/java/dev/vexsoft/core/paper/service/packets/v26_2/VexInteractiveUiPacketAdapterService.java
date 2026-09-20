package dev.vexsoft.core.paper.service.packets.v26_2;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.packets.interactiveui.InteractiveUiPacketInput;
import dev.vexsoft.core.paper.packets.interactiveui.InteractiveUiPacketTransport;
import dev.vexsoft.core.paper.packets.service.InteractiveUiPacketAdapterService;
import dev.vexsoft.core.paper.packets.service.PacketTransportAdapterService;
import dev.vexsoft.core.paper.packets.v26_2.interactiveui.V26_2InteractiveUiInputDecoder;
import dev.vexsoft.core.paper.packets.v26_2.interactiveui.V26_2InteractiveUiTargetPreserver;
import io.netty.buffer.Unpooled;
import io.papermc.paper.adventure.PaperAdventure;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBlockChangedAckPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetCameraPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

/** Implements a Minecraft 26.2 client-only camera and mounted rotation transport. */
@Dependencies(PacketTransportAdapterService.class)
public final class VexInteractiveUiPacketAdapterService implements InteractiveUiPacketAdapterService {

    private final PacketTransportAdapterService packets;
    private final Map<Integer, Restoration> restorations = new ConcurrentHashMap<>();

    public VexInteractiveUiPacketAdapterService(final VexServiceRegistry services) {
        packets = services.require(PacketTransportAdapterService.class);
    }

    @Override
    public InteractiveUiPacketTransport open(final Player player) {
        Objects.requireNonNull(player, "player");

        if (!Bukkit.isOwnedByCurrentRegion(player)) {
            throw new IllegalStateException("Interactive UI transport must open on the player's owning thread");
        }

        ServerPlayer nativePlayer = ((CraftPlayer) player).getHandle();

        if (player.getGameMode() != GameMode.SURVIVAL || player.isInsideVehicle()
            || !nativePlayer.onGround() || player.isSleeping() || player.isDead()
            || !player.getInventory().getItemInMainHand().isEmpty()) {
            throw new IllegalStateException("Interactive UI requires Survival, an empty main hand, and standing on foot");
        }

        Location origin = player.getLocation();
        int blockY = (int) Math.floor(nativePlayer.getBoundingBox().maxY) + 1;

        if (blockY < player.getWorld().getMinHeight() || blockY >= player.getWorld().getMaxHeight()) {
            throw new IllegalStateException("There is no room for the interactive UI camera at this height");
        }

        BlockPos block = new BlockPos(origin.getBlockX(), blockY, origin.getBlockZ());
        Vec3 cameraPosition = Vec3.atCenterOf(block);
        Display.BlockDisplay mount = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, nativePlayer.level());
        Display.BlockDisplay camera = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, nativePlayer.level());

        mount.setNoGravity(true);
        mount.setPos(nativePlayer.position());

        // Preserve the local player's position when the client applies its normal passenger attachment offsets.
        Vec3 ridingPosition = mount.getPassengerRidingPosition(nativePlayer)
            .subtract(nativePlayer.getVehicleAttachmentPoint(mount));
        mount.setPos(mount.position().add(nativePlayer.position().subtract(ridingPosition)));
        camera.setNoGravity(true);
        camera.setPos(cameraPosition);
        camera.setYRot(0.0F);
        camera.setXRot(0.0F);

        InteractiveUiPacketTransport transport = new InteractiveUiPacketTransport(
            player.getUniqueId(),
            player.getWorld().getUID(),
            mount.getId(),
            camera.getId(),
            block.getX(),
            block.getY(),
            block.getZ(),
            cameraPosition.x,
            cameraPosition.y,
            cameraPosition.z,
            0.0F,
            0.0F
        );
        Restoration restoration = new Restoration(
            controlRestore(nativePlayer, transport),
            blockRestore(nativePlayer, block),
            player.getUniqueId(),
            nativePlayer.connection
        );

        restorations.put(camera.getId(), restoration);

        try {
            List<Object> setup = new ArrayList<>();

            setup.addAll(spawn(mount));
            setup.addAll(spawn(camera));
            // VoxelShape.clip returns an inside hit when the fixed camera begins inside this full cube.
            setup.add(new ClientboundBlockUpdatePacket(block, Blocks.BEDROCK.defaultBlockState()));
            setup.add(passengers(mount.getId(), nativePlayer.getId()));
            setup.add(new ClientboundSetCameraPacket(camera));
            setup.add(new ClientboundPlayerRotationPacket(0.0F, false, 0.0F, false));
            packets.sendBundle(player, setup);
            return transport;
        } catch (RuntimeException failure) {
            try {
                close(player, transport);
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }

            throw failure;
        }
    }

    @Override
    public void close(final Player player, final InteractiveUiPacketTransport transport) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(transport, "transport");

        if (!transport.getViewerId().equals(player.getUniqueId())) {
            throw new IllegalArgumentException("Interactive UI transport belongs to another viewer");
        }

        Restoration restoration = restorations.remove(transport.getCameraEntityId());

        if (restoration == null || !player.isOnline()) {
            return;
        }

        synchronized (restoration) {
            restoration.closed = true;
            restoration.pendingCursor = null;
        }

        boolean ownsPlayer = Bukkit.isOwnedByCurrentRegion(player);
        ServerPlayer nativePlayer = ownsPlayer ? ((CraftPlayer) player).getHandle() : null;
        RuntimeException failure = null;

        failure = cleanup(failure, () -> restoration.connection.send(
            ClientboundBossEventPacket.createRemovePacket(restoration.scene.getId())
        ));
        failure = cleanup(failure, () -> restoration.connection.send(
            ClientboundBossEventPacket.createRemovePacket(restoration.cursor.getId())
        ));

        try {
            // Restore control before removing the camera entity, including a locally changed hotbar selection.
            sendRestoration(restoration, ownsPlayer ? controlRestore(nativePlayer, transport) : restoration.controls);
        } catch (RuntimeException cleanupFailure) {
            if (failure == null) {
                failure = cleanupFailure;
            } else {
                failure.addSuppressed(cleanupFailure);
            }
        }

        try {
            if (!ownsPlayer) {
                // World changes retire the session on the owner thread; shutdown can use its captured world snapshot.
                sendRestoration(restoration, restoration.block);
            } else if (transport.getWorldId().equals(player.getWorld().getUID())) {
                BlockPos position = new BlockPos(transport.getBlockX(), transport.getBlockY(), transport.getBlockZ());
                boolean ownsBlock = Bukkit.isOwnedByCurrentRegion(
                    player.getWorld(), position.getX() >> 4, position.getZ() >> 4
                );

                sendRestoration(restoration, ownsBlock ? blockRestore(nativePlayer, position) : restoration.block);
            }
        } catch (RuntimeException cleanupFailure) {
            if (failure == null) {
                failure = cleanupFailure;
            } else {
                failure.addSuppressed(cleanupFailure);
            }
        }

        if (failure != null) {
            throw failure;
        }
    }

    @Override
    public void scene(final Player player, final InteractiveUiPacketTransport transport, final Component content) {
        present(player, transport, content, false);
    }

    @Override
    public void cursor(final Player player, final InteractiveUiPacketTransport transport, final Component content) {
        present(player, transport, content, true);
    }

    @Override
    public void cursorImmediate(final InteractiveUiPacketTransport transport, final Component content) {
        present(transport, content, true);
    }

    @Override
    public Optional<InteractiveUiPacketInput> decode(final Object packet) {
        return V26_2InteractiveUiInputDecoder.decode(packet);
    }

    @Override
    public Object preserveTarget(final InteractiveUiPacketTransport transport, final Object packet) {
        return V26_2InteractiveUiTargetPreserver.preserve(transport, packet);
    }

    @Override
    public void acknowledge(final Player player, final int sequence) {
        if (sequence < 0 || !player.isOnline()) {
            return;
        }

        if (Bukkit.isOwnedByCurrentRegion(player)) {
            packets.send(player, new ClientboundBlockChangedAckPacket(sequence));
        } else {
            UUID viewerId = player.getUniqueId();

            restorations.values().stream()
                .filter(restoration -> restoration.viewerId.equals(viewerId))
                .findFirst()
                .ifPresent(restoration -> restoration.connection.send(new ClientboundBlockChangedAckPacket(sequence)));
        }
    }

    private void present(
        final Player player,
        final InteractiveUiPacketTransport transport,
        final Component content,
        final boolean cursor
    ) {
        if (!transport.getViewerId().equals(player.getUniqueId())) {
            throw new IllegalArgumentException("Interactive UI transport belongs to another viewer");
        }

        present(transport, content, cursor);
    }

    private void present(
        final InteractiveUiPacketTransport transport,
        final Component content,
        final boolean cursor
    ) {
        Restoration restoration = restorations.get(transport.getCameraEntityId());

        if (restoration == null) {
            return;
        }

        if (!restoration.viewerId.equals(transport.getViewerId())) {
            throw new IllegalArgumentException("Interactive UI transport belongs to another viewer");
        }

        synchronized (restoration) {
            if (restoration.closed || restorations.get(transport.getCameraEntityId()) != restoration) {
                return;
            }

            Objects.requireNonNull(content, "content");
            var eventLoop = restoration.connection.connection.channel.eventLoop();

            if (!cursor) {
                // Always enqueue, including on Netty, so caller publication order also determines packet order.
                eventLoop.execute(() -> presentOwned(transport, restoration, content, false));
                return;
            }

            restoration.pendingCursor = content;

            if (restoration.cursorQueued) {
                return;
            }

            restoration.cursorQueued = true;

            try {
                eventLoop.execute(() -> flushCursor(transport, restoration));
            } catch (RuntimeException failure) {
                restoration.cursorQueued = false;
                restoration.pendingCursor = null;
                throw failure;
            }
        }
    }

    private void flushCursor(final InteractiveUiPacketTransport transport, final Restoration restoration) {
        synchronized (restoration) {
            Component content = restoration.pendingCursor;

            restoration.pendingCursor = null;
            restoration.cursorQueued = false;

            if (content != null) {
                presentOwned(transport, restoration, content, true);
            }
        }
    }

    private void presentOwned(
        final InteractiveUiPacketTransport transport,
        final Restoration restoration,
        final Component content,
        final boolean cursor
    ) {
        synchronized (restoration) {
            if (restoration.closed || restorations.get(transport.getCameraEntityId()) != restoration) {
                return;
            }

            // Bossbar order determines the shader carrier position; input can arrive before the first scene frame.
            if (cursor && !restoration.sceneShown) {
                return;
            }

            BossEvent event = cursor ? restoration.cursor : restoration.scene;
            boolean shown = cursor ? restoration.cursorShown : restoration.sceneShown;

            event.setName(PaperAdventure.asVanilla(Objects.requireNonNull(content, "content")));
            restoration.connection.send(shown
                ? ClientboundBossEventPacket.createUpdateNamePacket(event)
                : ClientboundBossEventPacket.createAddPacket(event));

            if (cursor) {
                restoration.cursorShown = true;
            } else {
                restoration.sceneShown = true;
            }
        }
    }

    private static RuntimeException cleanup(final RuntimeException previous, final Runnable cleanup) {
        try {
            cleanup.run();
            return previous;
        } catch (RuntimeException failure) {
            if (previous == null) {
                return failure;
            }

            previous.addSuppressed(failure);
            return previous;
        }
    }

    private static void sendRestoration(final Restoration restoration, final List<Object> restorePackets) {
        RuntimeException failure = null;

        for (Object message : restorePackets) {
            if (!(message instanceof Packet<?> packet)) {
                throw new IllegalArgumentException("Interactive UI cleanup requires native packets");
            }

            failure = cleanup(failure, () -> restoration.connection.send(packet));
        }

        if (failure != null) {
            throw failure;
        }
    }

    private static List<Object> controlRestore(
        final ServerPlayer nativePlayer,
        final InteractiveUiPacketTransport transport
    ) {
        return List.of(
            new ClientboundSetCameraPacket(nativePlayer),
            passengers(transport.getMountEntityId()),
            new ClientboundPlayerRotationPacket(nativePlayer.getYRot(), false, nativePlayer.getXRot(), false),
            new ClientboundSetHeldSlotPacket(nativePlayer.getInventory().getSelectedSlot()),
            new ClientboundContainerSetContentPacket(
                nativePlayer.inventoryMenu.containerId,
                nativePlayer.inventoryMenu.getStateId(),
                nativePlayer.inventoryMenu.getItems().stream().map(ItemStack::copy).toList(),
                nativePlayer.inventoryMenu.getCarried().copy()
            ),
            new ClientboundRemoveEntitiesPacket(transport.getMountEntityId(), transport.getCameraEntityId())
        );
    }

    private static List<Object> blockRestore(final ServerPlayer nativePlayer, final BlockPos position) {
        List<Object> restore = new ArrayList<>();

        restore.add(new ClientboundBlockUpdatePacket(nativePlayer.level(), position));
        BlockEntity blockEntity = nativePlayer.level().getBlockEntity(position);

        if (blockEntity != null && blockEntity.getUpdatePacket() != null) {
            restore.add(blockEntity.getUpdatePacket());
        }

        return List.copyOf(restore);
    }

    private static ClientboundSetPassengersPacket passengers(final int vehicleId, final int... passengerIds) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        try {
            // The public codec avoids reflective field mutation and never mounts a real server entity.
            buffer.writeVarInt(vehicleId);
            buffer.writeVarIntArray(passengerIds);
            return ClientboundSetPassengersPacket.STREAM_CODEC.decode(buffer);
        } finally {
            buffer.release();
        }
    }

    private static List<Object> spawn(final Entity entity) {
        return List.of(
            new ClientboundAddEntityPacket(
                entity.getId(),
                entity.getUUID(),
                entity.getX(),
                entity.getY(),
                entity.getZ(),
                entity.getXRot(),
                entity.getYRot(),
                entity.getType(),
                0,
                Vec3.ZERO,
                entity.getYHeadRot()
            ),
            new ClientboundSetEntityDataPacket(entity.getId(), entity.getEntityData().packAll())
        );
    }

    private static BossEvent bossBar() {
        BossEvent event = new BossEvent(
            UUID.randomUUID(),
            PaperAdventure.asVanilla(Component.empty()),
            BossEvent.BossBarColor.PURPLE,
            BossEvent.BossBarOverlay.PROGRESS
        ) {
        };

        event.setProgress(0.0F);
        return event;
    }

    /** Holds captured cleanup packets and serializes presentation changes against session shutdown. */
    @RequiredArgsConstructor
    private static final class Restoration {

        private final List<Object> controls;
        private final List<Object> block;
        private final UUID viewerId;
        private final ServerGamePacketListenerImpl connection;
        private final BossEvent scene = bossBar();
        private final BossEvent cursor = bossBar();
        private boolean sceneShown;
        private boolean cursorShown;
        private Component pendingCursor;
        private boolean cursorQueued;
        private boolean closed;
    }
}
