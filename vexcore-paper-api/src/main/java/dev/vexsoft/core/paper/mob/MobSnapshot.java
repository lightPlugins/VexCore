package dev.vexsoft.core.paper.mob;

import dev.vexsoft.core.paper.packets.display.DisplayGlowColor;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;

/** Immutable observable state of one runtime mob. */
public record MobSnapshot(MobHandle handle, UUID entityId, MobScope scope, Location location, double currentHealth,
                          double maxHealth, double scale, double movementSpeed, double rotationSpeed,
                          Optional<DisplayGlowColor> glow) {

    /** Creates a defensive mob snapshot. */
    public MobSnapshot {
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(scope, "scope");
        location = Objects.requireNonNull(location, "location").clone();
        glow = Objects.requireNonNull(glow, "glow");
    }

    @Override
    public Location location() {
        return location.clone();
    }
}
