package dev.vexsoft.core.paper.vexui;

import java.util.Objects;
import net.kyori.adventure.text.Component;

/** Localized choice with a stable identifier and an explicit availability state. */
public record VexUIOption(String id, Component label, Component tooltip, boolean enabled) {

    /** Validates a stable identifier and prelocalized presentation. */
    public VexUIOption {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Options require a stable identifier");
        }
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(tooltip, "tooltip");
    }

    /** Creates an enabled choice without a tooltip. */
    public VexUIOption(final String id, final Component label) {
        this(id, label, Component.empty(), true);
    }
}
