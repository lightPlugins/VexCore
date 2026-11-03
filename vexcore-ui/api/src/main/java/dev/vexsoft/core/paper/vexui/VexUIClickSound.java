package dev.vexsoft.core.paper.vexui;

import java.util.Objects;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;

/** Immutable button feedback; null overrides inherit the frame or service owner's setting. */
public record VexUIClickSound(boolean enabled, Key key, float volume, float pitch) {

    /** Validates a sound key, nonnegative volume and a pitch within Minecraft's supported range. */
    public VexUIClickSound {
        Objects.requireNonNull(key, "key");
        if (!Float.isFinite(volume) || volume < 0 || !Float.isFinite(pitch) || pitch < 0.5F || pitch > 2.0F) {
            throw new IllegalArgumentException("Click sounds need finite volume >= 0 and pitch between 0.5 and 2");
        }
    }

    /** Returns the quiet, slightly higher-pitched default button click. */
    public static VexUIClickSound defaults() {
        return new VexUIClickSound(true, Key.key("minecraft:ui.button.click"), 0.4F, 1.25F);
    }

    /** Returns the shared collection confirmation; play only after a menu collection succeeds. */
    public static VexUIClickSound collect() {
        return new VexUIClickSound(true, Key.key("vexcore:ui.collect"), 0.65F, 1.0F);
    }

    /** Returns the shared pickup cue for each committed reward delivery. */
    public static VexUIClickSound pickUp() {
        return new VexUIClickSound(true, Key.key("vexcore:ui.pick-up"), 0.65F, 1.0F);
    }

    /** Returns an explicit mute setting, overriding inherited feedback. */
    public static VexUIClickSound disabled() {
        return new VexUIClickSound(false, Key.key("minecraft:ui.button.click"), 0.4F, 1.25F);
    }

    /** Builds the client UI-category sound; callers must check enabled before playing it. */
    public Sound asSound() {
        return Sound.sound(key, Sound.Source.UI, volume, pitch);
    }
}
