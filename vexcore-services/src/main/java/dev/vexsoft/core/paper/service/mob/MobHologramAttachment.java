package dev.vexsoft.core.paper.service.mob;

import dev.vexsoft.core.paper.packets.display.FakeDisplayHandle;
import dev.vexsoft.core.paper.packets.display.FakePassengerMount;
import dev.vexsoft.core.paper.packets.service.DisplayPassengerPacketService;
import dev.vexsoft.core.paper.packets.service.TextDisplayPacketService;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/** Keeps a mounted mob hologram upright when the client attaches it to its vehicle. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MobHologramAttachment {

    /** Copies the carrier position without inheriting its vertical look direction. */
    public static Location uprightLocation(final Location carrier) {
        Location location = carrier.clone();
        location.setPitch(0.0F);
        return location;
    }

    /** Restores the display pitch after the passenger packet, including repeated attachments. */
    public static void attach(
        final Player viewer,
        final Entity carrier,
        final FakeDisplayHandle handle,
        final float offset,
        final DisplayPassengerPacketService passengers,
        final TextDisplayPacketService displays
    ) {
        passengers.addFakePassenger(viewer, carrier, new FakePassengerMount(handle, 0.0F, offset, 0.0F));
        // The client can copy the vehicle rotation while processing the passenger packet.
        displays.teleport(handle, uprightLocation(carrier.getLocation()));
    }
}
