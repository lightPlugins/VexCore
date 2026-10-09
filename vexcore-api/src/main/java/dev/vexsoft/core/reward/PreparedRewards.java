package dev.vexsoft.core.reward;

import java.util.List;
import java.util.Objects;

/** A once-selected set of action rewards that can be retried without rerolling. */
public record PreparedRewards(List<Entry> entries) {

    /** Copies the prepared entries. */
    public PreparedRewards {
        entries = List.copyOf(entries);
    }

    /** One selected action and its original announcement settings. */
    public record Entry(String key, RewardOptions options, CompiledReward reward) {

        /** Validates the prepared action. */
        public Entry {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(options, "options");
            Objects.requireNonNull(reward, "reward");
            if (reward.getBehavior() != RewardBehavior.ACTION) {
                throw new IllegalArgumentException("Prepared rewards must be actions");
            }
        }
    }
}
