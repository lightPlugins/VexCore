package dev.vexsoft.core.paper.service.packets;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.packets.block.VirtualBlock;
import dev.vexsoft.core.paper.packets.service.VirtualBlockService;
import dev.vexsoft.core.paper.packets.service.PacketConnectionAdapterService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Publishes immutable chunk snapshots and keeps personal overlays across reconnects in RAM. */
@Dependencies(PacketConnectionAdapterService.class)
public final class VexVirtualBlockService implements VirtualBlockService, AutoCloseable {
    private final PacketConnectionAdapterService connections;
    private final Map<Scope, Map<Position, VirtualBlock>> chunks = new ConcurrentHashMap<>();
    private final Map<UUID, String> worlds = new ConcurrentHashMap<>();
    private final Map<UUID, ConcurrentSkipListMap<Integer, Position>> predictions = new ConcurrentHashMap<>();

    public VexVirtualBlockService(final VexServiceRegistry services) {
        connections = services.require(PacketConnectionAdapterService.class);
    }

    @Override
    public void put(final String world, final UUID audience, final VirtualBlock block) {
        Scope scope = new Scope(world, audience, block.x() >> 4, block.z() >> 4);
        Position position = new Position(block.x(), block.y(), block.z());
        chunks.compute(scope, (ignored, previous) -> {
            Map<Position, VirtualBlock> next = previous == null ? new HashMap<>() : new HashMap<>(previous);
            next.put(position, block);
            return Map.copyOf(next);
        });
        send(world, audience, block);
    }

    @Override
    public void remove(final String world, final UUID audience, final int x, final int y, final int z) {
        Scope scope = new Scope(world, audience, x >> 4, z >> 4);
        Position position = new Position(x, y, z);
        VirtualBlock[] removed = {null};
        chunks.computeIfPresent(scope, (ignored, previous) -> {
            Map<Position, VirtualBlock> next = new HashMap<>(previous);
            removed[0] = next.remove(position);
            return next.isEmpty() ? null : Map.copyOf(next);
        });
        if (removed[0] != null) {
            send(world, audience, removed[0]);
        }
    }

    @Override
    public void bind(final UUID viewer, final String world) {
        String previous = worlds.put(viewer, world);
        if (!world.equals(previous)) {
            predictions.remove(viewer);
        }
    }

    @Override
    public VirtualBlock find(final UUID viewer, final int x, final int y, final int z) {
        String world = worlds.get(viewer);
        if (world == null) {
            return null;
        }
        Position position = new Position(x, y, z);
        // Old shared cooldowns retain their scope when a region changes to personal mode.
        VirtualBlock shared = chunks.getOrDefault(new Scope(world, null, x >> 4, z >> 4), Map.of()).get(position);
        return shared != null ? shared
            : chunks.getOrDefault(new Scope(world, viewer, x >> 4, z >> 4), Map.of()).get(position);
    }

    @Override
    public List<VirtualBlock> chunk(final UUID viewer, final int x, final int z) {
        String world = worlds.get(viewer);
        if (world == null) {
            return List.of();
        }
        Map<Position, VirtualBlock> result = new LinkedHashMap<>(
            chunks.getOrDefault(new Scope(world, viewer, x, z), Map.of()));
        result.putAll(chunks.getOrDefault(new Scope(world, null, x, z), Map.of()));
        return List.copyOf(result.values());
    }

    @Override
    public void predict(final UUID viewer, final int sequence, final int x, final int y, final int z) {
        if (sequence < 0) {
            return;
        }
        var pending = predictions.computeIfAbsent(viewer, ignored -> new ConcurrentSkipListMap<>());
        pending.put(sequence, new Position(x, y, z));
        // Bound malformed clients without retaining every action until disconnect.
        while (pending.size() > 1024) {
            pending.pollFirstEntry();
        }
    }

    @Override
    public List<VirtualBlock> acknowledge(final UUID viewer, final int sequence) {
        var pending = predictions.get(viewer);
        if (pending == null) {
            return List.of();
        }
        Map<Position, VirtualBlock> result = new LinkedHashMap<>();
        while (!pending.isEmpty() && pending.firstKey() <= sequence) {
            var entry = pending.pollFirstEntry();
            if (entry == null) {
                continue;
            }
            Position position = entry.getValue();
            VirtualBlock block = find(viewer, position.x, position.y, position.z);
            if (block != null) {
                result.put(position, block);
            }
        }
        return List.copyOf(result.values());
    }

    @Override
    public void disconnect(final UUID viewer) {
        worlds.remove(viewer);
        predictions.remove(viewer);
    }

    @Override
    public void synchronize(final Player viewer) {
        bind(viewer.getUniqueId(), viewer.getWorld().getKey().asString());
        for (long key : connections.getSentChunkKeys(viewer)) {
            for (VirtualBlock block : chunk(viewer.getUniqueId(), (int) key, (int) (key >> 32))) {
                viewer.sendBlockChange(new Location(viewer.getWorld(), block.x(), block.y(), block.z()),
                    block.replacement());
            }
        }
    }

    @Override
    public void close() {
        // Reveal originals before retiring snapshots while connections still exist.
        for (Scope scope : new ArrayList<>(chunks.keySet())) {
            for (VirtualBlock block : chunks.getOrDefault(scope, Map.of()).values()) {
                remove(scope.world, scope.audience, block.x(), block.y(), block.z());
            }
        }
        worlds.clear();
        predictions.clear();
    }

    private void send(final String world, final UUID audience, final VirtualBlock changed) {
        long key = (changed.x() >> 4) & 0xffffffffL | (long) (changed.z() >> 4) << 32;
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.getWorld().getKey().asString().equals(world)
                || audience != null && !audience.equals(viewer.getUniqueId())) {
                continue;
            }
            bind(viewer.getUniqueId(), world);
            if (connections.isChunkSent(viewer, key)
                && viewer.getWorld().isChunkLoaded(changed.x() >> 4, changed.z() >> 4)) {
                VirtualBlock current = find(viewer.getUniqueId(), changed.x(), changed.y(), changed.z());
                viewer.sendBlockChange(new Location(viewer.getWorld(), changed.x(), changed.y(), changed.z()),
                    current == null ? viewer.getWorld().getBlockAt(changed.x(), changed.y(), changed.z()).getBlockData()
                        : current.replacement());
            }
        }
    }

    private record Scope(String world, UUID audience, int x, int z) {
    }

    private record Position(int x, int y, int z) {
    }
}
