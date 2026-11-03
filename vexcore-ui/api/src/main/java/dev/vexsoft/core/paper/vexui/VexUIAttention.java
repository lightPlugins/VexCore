package dev.vexsoft.core.paper.vexui;

/** Independent reward/new-content signal; reading new content never consumes an available reward. */
public record VexUIAttention(int rewards, boolean fresh) {

    /** Requires a nonnegative count of independently claimable bundles. */
    public VexUIAttention {
        if (rewards < 0) {
            throw new IllegalArgumentException("Attention reward counts cannot be negative");
        }
    }

    /** Returns whether navigation should be highlighted. */
    public boolean highlighted() {
        return rewards > 0 || fresh;
    }

    /** Returns ordinary presentation without attention markers. */
    public static VexUIAttention none() {
        return new VexUIAttention(0, false);
    }
}
