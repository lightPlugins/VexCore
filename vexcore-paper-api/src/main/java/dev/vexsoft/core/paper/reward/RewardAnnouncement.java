package dev.vexsoft.core.paper.reward;

import org.bukkit.NamespacedKey;

/** One reusable localized reward announcement and its optional sound. */
public record RewardAnnouncement(String localizationKey, String sound, float volume, float pitch) {

    /** Validates the localization key and the optional namespaced sound. */
    public RewardAnnouncement {
        if (localizationKey == null || localizationKey.isBlank()
            || sound != null && (!sound.contains(":") || NamespacedKey.fromString(sound) == null)
            || !Float.isFinite(volume) || volume < 0 || !Float.isFinite(pitch) || pitch <= 0) {
            throw new IllegalArgumentException("Invalid reward announcement");
        }
    }
}
