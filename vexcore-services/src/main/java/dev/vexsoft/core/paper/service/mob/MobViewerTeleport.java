package dev.vexsoft.core.paper.service.mob;

import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.bukkit.Location;
import org.bukkit.event.player.PlayerTeleportEvent;

/** Distinguishes actual viewer relocation from position corrections and rotation-only teleports. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MobViewerTeleport {

    public static boolean changesPosition(final PlayerTeleportEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();

        return !event.isCancelled() && to != null && (!Objects.equals(from.getWorld(), to.getWorld())
            || from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ());
    }
}
