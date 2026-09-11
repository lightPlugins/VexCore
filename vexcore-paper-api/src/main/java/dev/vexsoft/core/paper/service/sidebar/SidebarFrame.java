package dev.vexsoft.core.paper.service.sidebar;

import java.util.List;
import java.util.Objects;
import net.kyori.adventure.text.Component;

/** Immutable title and ordered lines rendered in a player's sidebar. */
public record SidebarFrame(Component title, List<Component> lines) {

    public static final int MAXIMUM_LINES = 15;

    /** Validates and defensively copies one sidebar frame. */
    public SidebarFrame {
        title = Objects.requireNonNull(title, "title");
        lines = List.copyOf(Objects.requireNonNull(lines, "lines"));

        if (lines.size() > MAXIMUM_LINES) {
            throw new IllegalArgumentException("A sidebar must not exceed 15 lines");
        }

        lines.forEach(line -> Objects.requireNonNull(line, "line"));
    }

    /** Returns the immutable Adventure title component. */
    @Override
    public Component title() {
        return Component.empty().append(title);
    }
}
