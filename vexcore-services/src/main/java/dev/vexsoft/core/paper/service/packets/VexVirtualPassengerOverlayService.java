package dev.vexsoft.core.paper.service.packets;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.packets.display.FakeDisplayHandle;
import dev.vexsoft.core.paper.packets.service.DisplayPacketAdapterService;
import dev.vexsoft.core.paper.packets.service.VirtualPassengerOverlayService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Stores per-viewer virtual passengers outside third-party renderer state. */
@Dependencies(DisplayPacketAdapterService.class)
public final class VexVirtualPassengerOverlayService implements VirtualPassengerOverlayService, AutoCloseable {

    private final DisplayPacketAdapterService adapter;
    private final Map<UUID, Map<Integer, List<Integer>>> passengers = new ConcurrentHashMap<>();
    private final Map<UUID, Map<Integer, List<Integer>>> nativePassengers = new ConcurrentHashMap<>();

    public VexVirtualPassengerOverlayService(final VexServiceRegistry services) {
        adapter = services.require(DisplayPacketAdapterService.class);
    }

    @Override
    public boolean add(final int vehicleEntityId, final FakeDisplayHandle passenger) {
        Map<Integer, List<Integer>> viewerMounts = passengers.computeIfAbsent(passenger.getViewerId(),
            ignored -> new ConcurrentHashMap<>());
        boolean[] added = {false};
        viewerMounts.compute(vehicleEntityId, (ignored, current) -> {
            if (current != null && current.contains(passenger.getEntityId())) {
                return current;
            }
            List<Integer> next = new ArrayList<>(current == null ? List.of() : current);
            next.add(passenger.getEntityId());
            added[0] = true;
            return List.copyOf(next);
        });
        if (added[0]) {
            sendMount(passenger.getViewerId(), vehicleEntityId);
        }
        return added[0];
    }

    @Override
    public void remove(final int vehicleEntityId, final FakeDisplayHandle passenger) {
        Map<Integer, List<Integer>> viewerMounts = passengers.get(passenger.getViewerId());
        if (viewerMounts == null) {
            return;
        }
        boolean[] removed = {false};
        viewerMounts.computeIfPresent(vehicleEntityId, (ignored, current) -> {
            List<Integer> next = new ArrayList<>(current);
            removed[0] = next.remove(Integer.valueOf(passenger.getEntityId()));
            return next.isEmpty() ? null : List.copyOf(next);
        });
        if (removed[0]) {
            sendMount(passenger.getViewerId(), vehicleEntityId);
        }
    }

    @Override
    public void removeViewer(final UUID viewerId) {
        passengers.remove(viewerId);
        nativePassengers.remove(viewerId);
    }

    @Override
    public Object rewriteOutbound(final UUID viewerId, final Object packet) {
        return adapter.rewritePassengers(packet, vehicleId -> passengers(viewerId, vehicleId),
            (vehicleId, original) -> {
                List<Integer> overlay = passengers(viewerId, vehicleId);
                if (!overlay.isEmpty() && original.containsAll(overlay)) {
                    return;
                }
                nativePassengers.computeIfAbsent(viewerId, ignored -> new ConcurrentHashMap<>())
                    .put(vehicleId, List.copyOf(original));
            }, entityId -> {
                Map<Integer, List<Integer>> viewerNative = nativePassengers.get(viewerId);
                if (viewerNative != null) {
                    viewerNative.remove(entityId);
                }
            });
    }

    private List<Integer> passengers(final UUID viewerId, final int vehicleEntityId) {
        Map<Integer, List<Integer>> viewerMounts = passengers.get(viewerId);
        return viewerMounts == null ? List.of() : viewerMounts.getOrDefault(vehicleEntityId, List.of());
    }

    @Override
    public void close() {
        passengers.clear();
        nativePassengers.clear();
    }

    private void sendMount(final UUID viewerId, final int vehicleEntityId) {
        Map<Integer, List<Integer>> viewerNative = nativePassengers.get(viewerId);
        List<Integer> original = viewerNative == null ? null : viewerNative.get(vehicleEntityId);
        Player viewer = Bukkit.getPlayer(viewerId);
        if (original == null || viewer == null || !viewer.isOnline()) {
            return;
        }
        List<Integer> merged = new ArrayList<>(original);
        for (int id : passengers(viewerId, vehicleEntityId)) {
            if (!merged.contains(id)) {
                merged.add(id);
            }
        }
        adapter.setPassengers(viewer, vehicleEntityId, merged);
    }
}
