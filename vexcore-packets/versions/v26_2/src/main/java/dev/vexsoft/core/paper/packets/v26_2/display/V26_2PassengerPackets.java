package dev.vexsoft.core.paper.packets.v26_2.display;

import io.netty.buffer.Unpooled;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import lombok.experimental.UtilityClass;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.world.entity.Entity;

@UtilityClass
public class V26_2PassengerPackets {

    private static final Field VEHICLE = findField("vehicle", "f_133272_", "b");
    private static final Field PASSENGERS = findField("passengers", "f_133273_", "c");

    public static Object create(final Entity packetVehicle, final int vehicleEntityId, final int[] passengerEntityIds) {
        ClientboundSetPassengersPacket packet = new ClientboundSetPassengersPacket(packetVehicle);

        try {
            VEHICLE.setInt(packet, vehicleEntityId);
            PASSENGERS.set(packet, passengerEntityIds.clone());

            return packet;
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Unable to create fake passenger packet", exception);
        }
    }

    /** Preserves ModelEngine's packet and adds only the viewer's local passengers. */
    public static Object append(final Object packet, final IntFunction<List<Integer>> additionalPassengers) {
        return append(packet, additionalPassengers, (vehicle, nativePassengers) -> { }, ignored -> { });
    }

    /** Records the original list so late viewer-local mounts can be sent immediately. */
    public static Object append(final Object packet, final IntFunction<List<Integer>> additionalPassengers,
                                final BiConsumer<Integer, List<Integer>> nativePassengers) {
        return append(packet, additionalPassengers, nativePassengers, ignored -> { });
    }

    /** Also forgets cached vehicles when the client removes them. */
    public static Object append(final Object packet, final IntFunction<List<Integer>> additionalPassengers,
                                final BiConsumer<Integer, List<Integer>> nativePassengers,
                                final IntConsumer removedEntities) {
        return append(packet, additionalPassengers, nativePassengers, entity -> {
            removedEntities.accept(entity);
            return List.of();
        }, ignored -> Map.of(), () -> { });
    }

    /** Repairs relationships after replacement spawns without sending packets from the interceptor. */
    public static Object append(
        final Object packet,
        final IntFunction<List<Integer>> additionalPassengers,
        final BiConsumer<Integer, List<Integer>> nativePassengers,
        final IntFunction<List<Integer>> removedEntities,
        final IntFunction<Map<Integer, List<Integer>>> spawnedEntities,
        final Runnable resetEntities
    ) {
        if (packet instanceof ClientboundSetPassengersPacket mount) {
            return appendMount(mount, additionalPassengers, nativePassengers);
        }
        if (packet instanceof ClientboundRemoveEntitiesPacket removal) {
            return appendRemoval(removal, removedEntities);
        }
        if (packet instanceof ClientboundBundlePacket bundle) {
            return appendBundle(bundle, additionalPassengers, nativePassengers, removedEntities, spawnedEntities,
                resetEntities);
        }
        if (packet instanceof ClientboundAddEntityPacket spawn) {
            Map<Integer, List<Integer>> mounts = spawnedEntities.apply(spawn.getId());
            if (!mounts.isEmpty()) {
                List<Packet<? super ClientGamePacketListener>> contents = new ArrayList<>();
                contents.add(spawn);
                mounts.forEach((vehicle, passengers) -> contents.add(mount(vehicle, passengers)));
                return new ClientboundBundlePacket(contents);
            }
        }
        if (packet instanceof ClientboundRespawnPacket) {
            resetEntities.run();
        }
        return packet;
    }

    private static ClientboundSetPassengersPacket appendMount(
        final ClientboundSetPassengersPacket mount,
        final IntFunction<List<Integer>> additionalPassengers,
        final BiConsumer<Integer, List<Integer>> nativePassengers
    ) {
        int[] original = mount.getPassengers();
        List<Integer> originalIds = new ArrayList<>(original.length);
        for (int id : original) {
            originalIds.add(id);
        }
        nativePassengers.accept(mount.getVehicle(), originalIds);
        List<Integer> additions = additionalPassengers.apply(mount.getVehicle());
        if (additions.isEmpty()) {
            return mount;
        }
        List<Integer> merged = new ArrayList<>(originalIds);
        for (int id : additions) {
            if (!merged.contains(id)) {
                merged.add(id);
            }
        }
        if (merged.size() == original.length) {
            return mount;
        }
        return mount(mount.getVehicle(), merged);
    }

    private static ClientboundSetPassengersPacket mount(final int vehicle, final List<Integer> passengers) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeVarInt(vehicle);
            buffer.writeVarIntArray(passengers.stream().mapToInt(Integer::intValue).toArray());
            return ClientboundSetPassengersPacket.STREAM_CODEC.decode(buffer);
        } finally {
            buffer.release();
        }
    }

    private static ClientboundBundlePacket appendBundle(
        final ClientboundBundlePacket bundle,
        final IntFunction<List<Integer>> additionalPassengers,
        final BiConsumer<Integer, List<Integer>> nativePassengers,
        final IntFunction<List<Integer>> removedEntities,
        final IntFunction<Map<Integer, List<Integer>>> spawnedEntities,
        final Runnable resetEntities
    ) {
        List<Packet<? super ClientGamePacketListener>> rewritten = new ArrayList<>();
        boolean changed = false;
        for (Packet<? super ClientGamePacketListener> nested : bundle.subPackets()) {
            Packet<? super ClientGamePacketListener> next = nested instanceof ClientboundSetPassengersPacket mount
                ? appendMount(mount, additionalPassengers, nativePassengers)
                : nested instanceof ClientboundBundlePacket inner
                    ? appendBundle(inner, additionalPassengers, nativePassengers, removedEntities, spawnedEntities,
                        resetEntities)
                    : nested instanceof ClientboundRemoveEntitiesPacket removal
                        ? appendRemoval(removal, removedEntities) : nested;
            if (nested instanceof ClientboundRespawnPacket) {
                resetEntities.run();
            }
            rewritten.add(next);
            if (nested instanceof ClientboundAddEntityPacket spawn) {
                Map<Integer, List<Integer>> mounts = spawnedEntities.apply(spawn.getId());
                mounts.forEach((vehicle, passengers) -> rewritten.add(mount(vehicle, passengers)));
                changed |= !mounts.isEmpty();
            }
            changed |= next != nested;
        }
        return changed ? new ClientboundBundlePacket(rewritten) : bundle;
    }

    private static ClientboundRemoveEntitiesPacket appendRemoval(
        final ClientboundRemoveEntitiesPacket removal,
        final IntFunction<List<Integer>> removedEntities
    ) {
        var ids = new LinkedHashSet<Integer>();
        removal.getEntityIds().forEach((int id) -> ids.add(id));
        removal.getEntityIds().forEach((int id) -> ids.addAll(removedEntities.apply(id)));
        if (ids.size() == removal.getEntityIds().size()) {
            return removal;
        }
        return new ClientboundRemoveEntitiesPacket(ids.stream().mapToInt(Integer::intValue).toArray());
    }

    private static Field findField(final String... names) {
        for (String name : names) {
            try {
                Field field = ClientboundSetPassengersPacket.class.getDeclaredField(name);

                field.setAccessible(true);

                return field;
            } catch (ReflectiveOperationException ignored) {
                // Names can differ between development and production mappings
            }
        }

        throw new IllegalStateException("Unable to resolve ClientboundSetPassengersPacket field " + List.of(names));
    }
}
