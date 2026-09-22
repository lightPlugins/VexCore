package dev.vexsoft.core.level;

/** Derived level and separately tracked reward progress for one instance. */
public record LevelInstanceSnapshot(LevelSnapshot progress, int claimedLevel, int nextRewardLevel,
                                    int claimableCount) {

}
