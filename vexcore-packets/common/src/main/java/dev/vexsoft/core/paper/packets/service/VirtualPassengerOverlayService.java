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

    /** Records native mount packets and merges viewer-local passengers into them. */
    Object rewriteOutbound(UUID viewerId, Object packet);
}
