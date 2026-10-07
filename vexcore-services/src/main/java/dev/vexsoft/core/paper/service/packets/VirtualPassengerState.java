package dev.vexsoft.core.paper.service.packets;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Tracks one client's entities independently of the server and third-party renderer. */
final class VirtualPassengerState {

    private final Map<Integer, List<Integer>> overlays = new HashMap<>();
    private final Map<Integer, List<Integer>> nativePassengers = new HashMap<>();
    private final Set<Integer> spawned = new HashSet<>();
    private final Set<Integer> virtualIds = new HashSet<>();

    synchronized boolean add(final int vehicle, final int passenger) {
        List<Integer> current = overlays.getOrDefault(vehicle, List.of());
        if (current.contains(passenger)) {
            return false;
        }
        var next = new ArrayList<>(current);
        next.add(passenger);
        virtualIds.add(passenger);
        overlays.put(vehicle, List.copyOf(next));
        if (spawned.contains(vehicle)) {
            nativePassengers.putIfAbsent(vehicle, List.of());
        }
        return true;
    }

    synchronized boolean remove(final int vehicle, final int passenger) {
        var next = new ArrayList<>(overlays.getOrDefault(vehicle, List.of()));
        if (!next.remove(Integer.valueOf(passenger))) {
            return false;
        }
        if (next.isEmpty()) {
            overlays.remove(vehicle);
        } else {
            overlays.put(vehicle, List.copyOf(next));
        }
        return true;
    }

    synchronized List<Integer> overlays(final int vehicle) {
        return overlays.getOrDefault(vehicle, List.of());
    }

    synchronized void recordNative(final int vehicle, final List<Integer> passengers) {
        // Keep retired IDs until their removal packet: queued mounts can arrive after server-side unregistration.
        nativePassengers.put(vehicle, passengers.stream().filter(id -> !virtualIds.contains(id)).toList());
    }

    synchronized List<Integer> removed(final int entityId) {
        spawned.remove(entityId);
        List<Integer> hidden = overlays(entityId);
        spawned.removeAll(hidden);
        if (overlays.values().stream().noneMatch(passengers -> passengers.contains(entityId))) {
            virtualIds.remove(entityId);
        }
        // A renderer can remove and respawn the same pivot without changing the server-side object.
        if (!overlays.containsKey(entityId)) {
            nativePassengers.remove(entityId);
        }
        return hidden;
    }

    synchronized boolean isSpawned(final int entityId) {
        return spawned.contains(entityId);
    }

    synchronized Map<Integer, List<Integer>> spawned(final int entityId) {
        spawned.add(entityId);
        if (overlays.containsKey(entityId)) {
            nativePassengers.putIfAbsent(entityId, List.of());
        }
        Map<Integer, List<Integer>> repairs = new HashMap<>();
        for (var entry : overlays.entrySet()) {
            int vehicle = entry.getKey();
            List<Integer> original = nativePassengers.get(vehicle);
            if (vehicle != entityId && !entry.getValue().contains(entityId)
                && (original == null || !original.contains(entityId))) {
                continue;
            }
            List<Integer> merged = mount(vehicle);
            if (merged != null) {
                repairs.put(vehicle, merged);
            }
        }
        return repairs;
    }

    synchronized List<Integer> mount(final int vehicle) {
        List<Integer> original = nativePassengers.get(vehicle);
        if (original == null || !spawned.contains(vehicle)) {
            return null;
        }
        var merged = new ArrayList<>(original);
        for (int passenger : overlays(vehicle)) {
            if (!merged.contains(passenger)) {
                merged.add(passenger);
            }
        }
        // Minecraft ignores missing passengers. Wait for their spawn packet rather than racing it.
        return spawned.containsAll(merged) ? List.copyOf(merged) : null;
    }

    synchronized void resetEntities() {
        spawned.clear();
        nativePassengers.clear();
    }
}
