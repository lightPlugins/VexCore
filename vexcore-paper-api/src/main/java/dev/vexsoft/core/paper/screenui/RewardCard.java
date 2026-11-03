package dev.vexsoft.core.paper.screenui;

import dev.vexsoft.core.reward.RewardNotification;
import java.util.Objects;
import net.kyori.adventure.key.Key;

/** Frozen reward content and the prefix of its build-generated, marked model preview fonts. */
public record RewardCard(RewardNotification reward, Key icon) {

    public RewardCard {
        Objects.requireNonNull(reward, "reward");
        Objects.requireNonNull(icon, "icon");
    }
}
