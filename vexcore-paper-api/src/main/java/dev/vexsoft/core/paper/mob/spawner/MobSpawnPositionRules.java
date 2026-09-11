package dev.vexsoft.core.paper.mob.spawner;

import java.util.Objects;
import java.util.Set;
import org.bukkit.Material;

/** Bounded cave-safe rules used to select ground spawn positions. */
public record MobSpawnPositionRules(double minimumRadius, double maximumRadius, int minimumYOffset, int maximumYOffset,
                                    int attempts, int clearanceBlocks, boolean avoidFluids, boolean avoidHazards,
                                    Set<Material> allowedSupportBlocks, Set<Material> deniedSupportBlocks) {

    /** Creates and validates spawn-position rules. */
    public MobSpawnPositionRules {
        if (!Double.isFinite(minimumRadius) || minimumRadius < 0.0D || !Double.isFinite(maximumRadius)
            || maximumRadius < minimumRadius) {
            throw new IllegalArgumentException("Invalid spawn radii");
        }

        if (minimumYOffset > maximumYOffset || attempts < 1 || clearanceBlocks < 1) {
            throw new IllegalArgumentException("Invalid vertical range, attempts, or clearance");
        }

        allowedSupportBlocks = Set.copyOf(Objects.requireNonNull(allowedSupportBlocks, "allowedSupportBlocks"));
        deniedSupportBlocks = Set.copyOf(Objects.requireNonNull(deniedSupportBlocks, "deniedSupportBlocks"));
    }

    /** Creates rules with conservative cave-safe defaults. */
    public static MobSpawnPositionRules defaults(final double radius) {
        return new MobSpawnPositionRules(0.0D, radius, -2, 3, 12, 3, true, true, Set.of(), Set.of());
    }
}
