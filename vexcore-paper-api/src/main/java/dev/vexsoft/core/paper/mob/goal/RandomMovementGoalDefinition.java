package dev.vexsoft.core.paper.mob.goal;

import java.util.Objects;
import java.util.Set;
import org.bukkit.Material;

/** Cave-safe random ground movement constrained around the spawn origin. */
public record RandomMovementGoalDefinition(int priority, double speed, double minimumDistance, double maximumDistance,
                                           double leashRadius, int minimumYOffset, int maximumYOffset, int attempts,
                                           int minimumIdleTicks, int maximumIdleTicks, int retryDelayTicks,
                                           boolean constrainEntirePath, boolean avoidFluids, boolean avoidHazards,
                                           Set<Material> allowedSupportBlocks,
                                           Set<Material> deniedSupportBlocks) implements MobGoalDefinition {

    /** Creates and validates a random movement goal. */
    public RandomMovementGoalDefinition {
        if (priority < 0 || !Double.isFinite(speed) || speed <= 0.0D || !Double.isFinite(minimumDistance)
            || minimumDistance < 0.0D || !Double.isFinite(maximumDistance) || maximumDistance <= minimumDistance
            || !Double.isFinite(leashRadius) || leashRadius < maximumDistance) {
            throw new IllegalArgumentException("Invalid random movement distances or speed");
        }

        if (minimumYOffset > maximumYOffset || attempts < 1 || minimumIdleTicks < 1
            || maximumIdleTicks < minimumIdleTicks || retryDelayTicks < 1) {
            throw new IllegalArgumentException("Invalid random movement bounds or timing");
        }

        allowedSupportBlocks = Set.copyOf(Objects.requireNonNull(allowedSupportBlocks, "allowedSupportBlocks"));
        deniedSupportBlocks = Set.copyOf(Objects.requireNonNull(deniedSupportBlocks, "deniedSupportBlocks"));
    }

    /** Creates a builder with safe ground-navigation defaults. */
    public static Builder builder() {
        return new Builder();
    }

    /** Builder for a random movement goal. */
    public static final class Builder {

        private int priority = 10;
        private double speed = 1.0D;
        private double minimumDistance = 2.0D;
        private double maximumDistance = 8.0D;
        private double leashRadius = 16.0D;
        private int minimumYOffset = -2;
        private int maximumYOffset = 3;
        private int attempts = 12;
        private int minimumIdleTicks = 40;
        private int maximumIdleTicks = 100;
        private int retryDelayTicks = 20;
        private boolean constrainEntirePath = true;
        private boolean avoidFluids = true;
        private boolean avoidHazards = true;
        private Set<Material> allowedSupportBlocks = Set.of();
        private Set<Material> deniedSupportBlocks = Set.of();

        private Builder() {
        }

        /** Sets the goal priority. */
        public Builder priority(final int value) {
            priority = value;

            return this;
        }

        /** Sets the native navigation speed multiplier. */
        public Builder speed(final double value) {
            speed = value;

            return this;
        }

        /** Sets minimum and maximum target distance. */
        public Builder targetDistance(final double minimum, final double maximum) {
            minimumDistance = minimum;
            maximumDistance = maximum;

            return this;
        }

        /** Sets the maximum horizontal distance from the spawn origin. */
        public Builder leashRadius(final double value) {
            leashRadius = value;

            return this;
        }

        /** Sets the allowed vertical offsets relative to the spawn origin. */
        public Builder verticalOffsets(final int minimum, final int maximum) {
            minimumYOffset = minimum;
            maximumYOffset = maximum;

            return this;
        }

        /** Sets the bounded candidate count used per target search. */
        public Builder attempts(final int value) {
            attempts = value;

            return this;
        }

        /** Sets the inclusive randomized idle interval. */
        public Builder idleTicks(final int minimum, final int maximum) {
            minimumIdleTicks = minimum;
            maximumIdleTicks = maximum;

            return this;
        }

        /** Sets the retry delay after no valid path could be found. */
        public Builder retryDelayTicks(final int value) {
            retryDelayTicks = value;

            return this;
        }

        /** Sets whether every path point must satisfy terrain and vertical rules. */
        public Builder constrainEntirePath(final boolean value) {
            constrainEntirePath = value;

            return this;
        }

        /** Sets whether candidates in fluids are rejected. */
        public Builder avoidFluids(final boolean value) {
            avoidFluids = value;

            return this;
        }

        /** Sets whether common damaging support blocks are rejected. */
        public Builder avoidHazards(final boolean value) {
            avoidHazards = value;

            return this;
        }

        /** Restricts valid support blocks; an empty set permits every safe solid block. */
        public Builder allowedSupportBlocks(final Set<Material> values) {
            allowedSupportBlocks = Set.copyOf(values);

            return this;
        }

        /** Rejects support blocks even when they also occur in the allowlist. */
        public Builder deniedSupportBlocks(final Set<Material> values) {
            deniedSupportBlocks = Set.copyOf(values);

            return this;
        }

        /** Creates the validated goal definition. */
        public RandomMovementGoalDefinition build() {
            return new RandomMovementGoalDefinition(
                priority,
                speed,
                minimumDistance,
                maximumDistance,
                leashRadius,
                minimumYOffset,
                maximumYOffset,
                attempts,
                minimumIdleTicks,
                maximumIdleTicks,
                retryDelayTicks,
                constrainEntirePath,
                avoidFluids,
                avoidHazards,
                allowedSupportBlocks,
                deniedSupportBlocks
            );
        }
    }
}
