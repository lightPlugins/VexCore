package dev.vexsoft.core.paper.packets.service;

import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.paper.packets.display.FakeDisplayHandle;
import java.util.UUID;

/** Adds viewer-local displays to outgoing virtual-entity passenger packets. */
public interface VirtualPassengerOverlayService extends VexService {

    /** Returns true when a new passenger was registered for this vehicle. */
    boolean add(int vehicleEntityId, FakeDisplayHandle passenger);

    /** Removes one viewer-local passenger without changing the vehicle's own passenger state. */
    void remove(int vehicleEntityId, FakeDisplayHandle passenger);

    /** Removes all registrations for a disconnected viewer. */
    void removeViewer(UUID viewerId);

    /** Returns whether this entity has been spawned and not removed on the viewer's current client. */
    boolean isSpawned(UUID viewerId, int entityId);

    /** Tracks client spawns and removals, hides orphan displays, and preserves viewer-local mounts. */
    Object rewriteOutbound(UUID viewerId, Object packet);
}
