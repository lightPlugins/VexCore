package dev.vexsoft.core.paper.vexui.action;

import java.util.Objects;
import net.kyori.adventure.text.Component;

/** Prelocalized button labels for an asynchronous action's four states. */
public record VexUIActionLabels(Component idle, Component running, Component success, Component error) {

    /** Requires all state labels so status remains understandable without color alone. */
    public VexUIActionLabels {
        Objects.requireNonNull(idle, "idle");
        Objects.requireNonNull(running, "running");
        Objects.requireNonNull(success, "success");
        Objects.requireNonNull(error, "error");
    }

    /** Returns the label matching a status snapshot. */
    public Component label(final VexUIActionStatus status) {
        return switch (status) {
            case IDLE -> idle;
            case RUNNING -> running;
            case SUCCESS -> success;
            case ERROR -> error;
        };
    }
}
