package dev.vexsoft.core.paper.action;

import dev.vexsoft.core.action.ActionRegistry;
import java.util.Set;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.entity.Player;

/** Standard player-only presentation actions for owner-scoped action registries. */
public final class PlayerActions {

    private PlayerActions() {
    }

    /** Registers the standard player presentation actions in the supplied action registry. */
    public static void register(ActionRegistry<Player> registry) {
        registry.register(
            "sound",
            section -> {
                for (String key : section.getValues(false).keySet()) {
                    if (!Set.of("type", "delay-ticks", "sound", "volume", "pitch").contains(key)) {
                        throw new IllegalArgumentException("Unknown sound parameter " + key);
                    }
                }

                String value = section.getString("sound", "");

                if (!value.contains(":")) {
                    throw new IllegalArgumentException("Sound requires a namespaced key");
                }

                Key key = Key.key(value);
                double volume = section.getDouble("volume", 1);
                double pitch = section.getDouble("pitch", 1);

                if (!Double.isFinite(volume) || volume < 0 || volume > Float.MAX_VALUE || !Double.isFinite(pitch)
                    || pitch <= 0 || pitch > Float.MAX_VALUE) {
                    throw new IllegalArgumentException("Invalid sound volume or pitch");
                }

                return player -> {
                    player.playSound(Sound.sound(key, Sound.Source.MASTER, (float) volume, (float) pitch));

                    return true;
                };
            }
        );
    }
}
