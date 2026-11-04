package dev.vexsoft.core.paper.vexui.template.crafting;

import dev.vexsoft.core.number.WholeAmount;
import dev.vexsoft.core.paper.vexui.template.VexUIIcon;
import java.util.List;
import java.util.Objects;
import net.kyori.adventure.text.Component;

/** Frozen exact-quantity presentation; the provider owns the opaque authoritative transaction token. */
public record VexUICraftingQuote(
    WholeAmount executions, WholeAmount resultAmount, List<Cost> costs, List<Component> requirements,
    boolean enabled, Component button, Object token
) {

    /** Copies presentation without rounding large virtual quantities or combining unlike variants. */
    public VexUICraftingQuote {
        Objects.requireNonNull(executions, "executions");
        Objects.requireNonNull(resultAmount, "resultAmount");
        Objects.requireNonNull(button, "button");
        Objects.requireNonNull(token, "token");
        costs = List.copyOf(costs);
        requirements = List.copyOf(requirements);
        if (!executions.isPositive() || !resultAmount.isPositive()) {
            throw new IllegalArgumentException("Crafting quantities must be positive");
        }
    }

    /** One exact material variant or currency with scaled requirement and current available balance. */
    public record Cost(Component name, VexUIIcon icon, WholeAmount required, WholeAmount available,
                       Component tooltip, Object token) {

        /** Creates a presentation-only cost without an authoritative provider token. */
        public Cost(final Component name, final VexUIIcon icon, final WholeAmount required,
                    final WholeAmount available, final Component tooltip) {
            this(name, icon, required, available, tooltip, null);
        }

        /** Requires positive costs and complete presentation. */
        public Cost {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(icon, "icon");
            Objects.requireNonNull(required, "required");
            Objects.requireNonNull(available, "available");
            Objects.requireNonNull(tooltip, "tooltip");
            if (!required.isPositive()) {
                throw new IllegalArgumentException("Crafting cost must be positive");
            }
        }

        /** Returns whether this individual cost is covered. */
        public boolean fulfilled() {
            return available.compareTo(required) >= 0;
        }
    }
}
