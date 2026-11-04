package dev.vexsoft.core.paper.packets.service.block;

import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.paper.packets.display.FakeDisplayHandle;
import dev.vexsoft.core.paper.packets.display.block.FakeBlockDamageOverlayRequest;
import dev.vexsoft.core.paper.packets.display.block.FakeBlockDamageOverlayUpdate;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Manages resource-pack-backed crack overlays for viewer-specific block displays. */
public interface BlockDamageOverlayPacketService extends VexService {

    /**
     * Spawns one independently transformable damage overlay for a viewer.
     *
     * @param viewer  player receiving the virtual overlay
     * @param request immutable overlay properties
     * @return viewer- and owner-bound display handle
     */
    FakeDisplayHandle spawn(Player viewer, FakeBlockDamageOverlayRequest request);

    /** Applies stage or transformation changes to a tracked overlay. */
    void update(FakeDisplayHandle handle, FakeBlockDamageOverlayUpdate update);

    /** Teleports a tracked overlay to another origin. */
    void teleport(FakeDisplayHandle handle, Location location);

    /** Removes one tracked overlay. */
    void remove(FakeDisplayHandle handle);

    /** Removes every overlay owned by this service for a viewer. */
    void removeAll(Player viewer);
}
