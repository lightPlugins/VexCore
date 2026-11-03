package dev.vexsoft.core.paper.vexui;

import java.util.Map;
import java.util.Objects;

/** Registers reusable component actions without silently replacing another component's handler. */
public final class VexUIActions {

    private VexUIActions() {
    }

    /** Registers a unique, nonblank frame-local action identifier. */
    public static void register(final Map<String, VexUIAction> actions, final String id, final VexUIAction action) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Component action identifiers must not be blank");
        }
        if (actions.putIfAbsent(id, Objects.requireNonNull(action, "action")) != null) {
            throw new IllegalArgumentException("Duplicate VexUI action " + id);
        }
    }
}
