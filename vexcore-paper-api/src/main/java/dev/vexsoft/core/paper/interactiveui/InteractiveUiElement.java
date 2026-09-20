package dev.vexsoft.core.paper.interactiveui;

import java.util.Objects;
import net.kyori.adventure.text.Component;

/** A labeled rectangle; insertion order defines drawing and hit-test order. */
public record InteractiveUiElement(String id, UiBounds bounds, Component label, int backgroundColor,
                                   boolean interactive) {

    /** Validates the stable identifier, rectangle, label, and RGB background. */
    public InteractiveUiElement {
        if (id == null || !id.matches("[a-zA-Z0-9_.:/-]{1,80}")) {
            throw new IllegalArgumentException("Invalid interactive element identifier");
        }
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(label, "label");
        if (backgroundColor < 0 || backgroundColor > 0xFFFFFF) {
            throw new IllegalArgumentException("Background must be an RGB color");
        }
    }
}
