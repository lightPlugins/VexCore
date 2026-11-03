package dev.vexsoft.core.paper.vexui;

import java.util.Objects;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;

/** Localized search form; text is submitted explicitly rather than sent for every keystroke. */
public record VexUISearch(Component title, Component label, Component submit, Component cancel,
                          String initial, Consumer<String> accepted) {

    /** Validates localized form labels, initial text and the submission callback. */
    public VexUISearch {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(submit, "submit");
        Objects.requireNonNull(cancel, "cancel");
        Objects.requireNonNull(initial, "initial");
        Objects.requireNonNull(accepted, "accepted");
    }
}
