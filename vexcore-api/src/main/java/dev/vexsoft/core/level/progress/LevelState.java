package dev.vexsoft.core.level.progress;

/** Combines experience-derived and persisted claim progression. */
public record LevelState(LevelSnapshot available, int claimedLevel, int nextClaimLevel) {

    /** Returns whether at least one reached level remains unclaimed. */
    public boolean hasClaimableLevel() {
        return nextClaimLevel <= available.level();
    }
}
