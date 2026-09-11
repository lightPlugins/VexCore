package dev.vexsoft.core.paper.mob;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/** Renders viewer-specific hologram content for a runtime mob. */
@FunctionalInterface
public interface MobHologramRenderer {

    /** Renders the complete hologram component for one viewer. */
    Component render(Player viewer, MobSnapshot mob);
}
