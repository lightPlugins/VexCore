package dev.vexsoft.core.paper.vexui.element;

import java.util.Locale;
import java.util.Objects;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;

/** Registered bitmap icon; vanilla sprites reference existing client PNGs rather than copying them. */
public record VexUIItemIcon(Key sprite, int pixels, Component tooltip) implements VexUIElement {

    /** Validates the registered sprite identifier and one of the supplied icon sizes. */
    public VexUIItemIcon {
        Objects.requireNonNull(sprite, "sprite");
        Objects.requireNonNull(tooltip, "tooltip");
        if (pixels != 16 && pixels != 32 && pixels != 48) {
            throw new IllegalArgumentException("Built-in icon sizes are 16, 32 and 48 pixels");
        }
    }

    @Override
    public VexUISize size() {
        return VexUISize.fixed(pixels + 1, pixels);
    }

    /** References a registered flat vanilla item sprite; block models need an explicit icon. */
    public static VexUIItemIcon vanilla(final Material material) {
        return new VexUIItemIcon(Key.key("minecraft", "item/" + material.name().toLowerCase(Locale.ROOT)),
            16, Component.empty());
    }

    /** Returns the sprite at another supported size. */
    public VexUIItemIcon size(final int size) {
        return new VexUIItemIcon(sprite, size, tooltip);
    }
}
