package dev.vexsoft.core.level;

import java.util.Objects;
import java.util.UUID;

/** Experience credited to a player's level instance after its transaction commits. */
public record LevelExperienceGain(UUID playerId, String instanceId, double amount, LevelChange change) {

    /** Validates the immutable notification data. */
    public LevelExperienceGain {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(change, "change");

        if (!Double.isFinite(amount) || amount <= 0.0D) {
            throw new IllegalArgumentException("Credited experience must be finite and positive");
        }
    }
}
