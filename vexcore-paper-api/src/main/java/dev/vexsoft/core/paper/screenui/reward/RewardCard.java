package dev.vexsoft.core.paper.screenui.reward;

import dev.vexsoft.core.reward.notification.RewardNotification;
import java.util.Objects;
import net.kyori.adventure.key.Key;

/** Frozen reward content and the prefix of its build-generated, marked model preview fonts. */
public record RewardCard(RewardNotification reward, Key icon) {

    /** Requires a granted reward and its generated icon font. */
    public RewardCard {
        Objects.requireNonNull(reward, "reward");
        Objects.requireNonNull(icon, "icon");
    }
}
