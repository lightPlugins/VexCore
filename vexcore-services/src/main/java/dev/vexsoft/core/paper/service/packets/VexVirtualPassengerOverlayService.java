package dev.vexsoft.core.paper.service.packets;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.packets.display.FakeDisplayHandle;
import dev.vexsoft.core.paper.packets.service.DisplayPacketAdapterService;
import dev.vexsoft.core.paper.packets.service.VirtualPassengerOverlayService;
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
    private final Map<UUID, VirtualPassengerState> viewers = new ConcurrentHashMap<>();

    public VexVirtualPassengerOverlayService(final VexServiceRegistry services) {
        adapter = services.require(DisplayPacketAdapterService.class);
    }

    @Override
    public boolean add(final int vehicleEntityId, final FakeDisplayHandle passenger) {
        boolean added = state(passenger.getViewerId()).add(vehicleEntityId, passenger.getEntityId());
        if (added) {
            sendMount(passenger.getViewerId(), vehicleEntityId);
        }
        return added;
    }

    @Override
    public void remove(final int vehicleEntityId, final FakeDisplayHandle passenger) {
        VirtualPassengerState state = viewers.get(passenger.getViewerId());
        if (state != null && state.remove(vehicleEntityId, passenger.getEntityId())) {
            sendMount(passenger.getViewerId(), vehicleEntityId);
        }
    }

    @Override
    public void removeViewer(final UUID viewerId) {
        viewers.remove(viewerId);
    }

    @Override
    public boolean isSpawned(final UUID viewerId, final int entityId) {
        VirtualPassengerState state = viewers.get(viewerId);
        return state != null && state.isSpawned(entityId);
    }

    @Override
    public Object rewriteOutbound(final UUID viewerId, final Object packet) {
        VirtualPassengerState state = state(viewerId);
        return adapter.rewritePassengers(packet, state::overlays, state::recordNative, state::removed, state::spawned,
            state::resetEntities);
    }

    @Override
    public void close() {
        viewers.clear();
    }

    private VirtualPassengerState state(final UUID viewerId) {
        return viewers.computeIfAbsent(viewerId, ignored -> new VirtualPassengerState());
    }

    private void sendMount(final UUID viewerId, final int vehicleEntityId) {
        VirtualPassengerState state = viewers.get(viewerId);
        List<Integer> merged = state == null ? null : state.mount(vehicleEntityId);
        Player viewer = Bukkit.getPlayer(viewerId);
        if (merged == null || viewer == null || !viewer.isOnline()) {
            return;
        }
        adapter.setPassengers(viewer, vehicleEntityId, merged);
    }
}
