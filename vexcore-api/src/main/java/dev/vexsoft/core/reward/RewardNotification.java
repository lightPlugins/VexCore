package dev.vexsoft.core.reward;

import java.math.BigDecimal;
import java.util.Objects;
import net.kyori.adventure.text.Component;

/** Frozen presentation of an actually granted reward; a null amount denotes a custom descriptive reward. */
public record RewardNotification(String identity, Component name, String icon, BigDecimal amount) {

    public RewardNotification {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(name, "name");
        icon = icon == null ? "minecraft:paper" : icon;
        if (amount != null && amount.signum() <= 0) {
            throw new IllegalArgumentException("Notification amounts must be positive");
        }
    }

    /** Combines identical resources without deriving amounts from formatted text. */
    public RewardNotification merge(RewardNotification other) {
        if (!identity.equals(other.identity) || !name.equals(other.name) || !icon.equals(other.icon)
            || amount == null || other.amount == null) {
            throw new IllegalArgumentException("Incompatible reward notifications");
        }
        return new RewardNotification(identity, name, icon, amount.add(other.amount));
    }
}
