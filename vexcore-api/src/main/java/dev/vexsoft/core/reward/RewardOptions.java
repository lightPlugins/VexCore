package dev.vexsoft.core.reward;

/** Shared probability and announcement options of one named reward. */
public record RewardOptions(double chance, String announcement) {

    /** Validates a probability between zero and one and an optional announcement type. */
    public RewardOptions {
        if (!Double.isFinite(chance) || chance < 0.0D || chance > 1.0D) {
            throw new IllegalArgumentException("Reward chance must be between zero and one");
        }
        if (announcement != null && !announcement.matches("[a-z][a-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid reward announcement type");
        }
    }

    /** Returns options for legacy guaranteed rewards without announcements. */
    public static RewardOptions guaranteed() {
        return new RewardOptions(1.0D, null);
    }
}
