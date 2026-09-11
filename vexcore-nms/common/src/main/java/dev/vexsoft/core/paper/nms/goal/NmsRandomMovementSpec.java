package dev.vexsoft.core.paper.nms.goal;

import java.util.Objects;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.Material;

/** Version-neutral data required by the native random movement goal. */
public record NmsRandomMovementSpec(int priority, double speed, double minimumDistance, double maximumDistance,
                                    double leashRadius, int minimumYOffset, int maximumYOffset, int attempts,
                                    int minimumIdleTicks, int maximumIdleTicks, int retryDelayTicks,
                                    boolean constrainEntirePath, boolean avoidFluids, boolean avoidHazards,
                                    Set<Material> allowedSupportBlocks, Set<Material> deniedSupportBlocks,
                                    Location origin) {

    public NmsRandomMovementSpec {
        allowedSupportBlocks = Set.copyOf(Objects.requireNonNull(allowedSupportBlocks, "allowedSupportBlocks"));
        deniedSupportBlocks = Set.copyOf(Objects.requireNonNull(deniedSupportBlocks, "deniedSupportBlocks"));
        origin = Objects.requireNonNull(origin, "origin").clone();
    }

    @Override
    public Location origin() {
        return origin.clone();
    }
}
